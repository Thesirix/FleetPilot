package com.fleetpilot.driver.sync;

import java.io.File;
import java.util.List;

import com.fleetpilot.driver.data.api.ApiException;
import com.fleetpilot.driver.data.local.OutboxItem;

/**
 * Sends the waiting items, oldest first: the record, then each of its photos. An item refused by the server is kept
 * aside with the reason and the next ones go on; a network failure stops the run, the next items would fail the same
 * way. Progress is saved after every step, so a claim half sent resumes at the next photo.
 */
public final class OutboxSender {

    /** Where the items wait (Room in the app, a list in the tests). */
    public interface Store {
        List<OutboxItem> pending();

        void save(OutboxItem item);

        void delete(String id);
    }

    /** What sends an item to the server (the REST API in the app, a fake in the tests). */
    public interface Transport {
        void sendRecord(OutboxItem item) throws ApiException;

        void sendPhoto(OutboxItem item, String photoId, File file) throws ApiException;
    }

    /** What a run did. */
    public record Report(int sent, int rejected, boolean interrupted) {
    }

    private final Store store;
    private final Transport transport;

    public OutboxSender(Store store, Transport transport) {
        this.store = store;
        this.transport = transport;
    }

    public Report flush() {
        int sent = 0;
        int rejected = 0;
        for (OutboxItem item : store.pending()) {
            try {
                send(item);
                store.delete(item.id);
                deletePhotos(item);
                sent++;
            } catch (ApiException e) {
                item.attempts++;
                if (!e.isTransient()) {
                    item.errorCode = e.code();
                    item.errorMessage = e.getMessage();
                    store.save(item);
                    rejected++;
                    continue;
                }
                store.save(item);
                return new Report(sent, rejected, true);
            }
        }
        return new Report(sent, rejected, false);
    }

    private void send(OutboxItem item) throws ApiException {
        if (!item.recordSent) {
            transport.sendRecord(item);
            item.recordSent = true;
            store.save(item);
        }
        List<String> sent = item.sentPhotos();
        for (String path : item.photos()) {
            File file = new File(path);
            String photoId = photoId(file);
            if (sent.contains(photoId)) {
                continue;
            }
            if (!file.exists()) {
                // Removed by the system (storage cleaned): nothing left to send
                item.markPhotoSent(photoId);
                store.save(item);
                continue;
            }
            transport.sendPhoto(item, photoId, file);
            item.markPhotoSent(photoId);
            store.save(item);
        }
    }

    /** The photo files are named after their id: 3f1c...e2.jpg */
    public static String photoId(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static void deletePhotos(OutboxItem item) {
        for (String path : item.photos()) {
            File file = new File(path);
            if (file.exists() && !file.delete()) {
                file.deleteOnExit();
            }
        }
    }
}
