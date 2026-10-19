package com.fleetpilot.driver.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.fleetpilot.driver.data.api.ApiException;
import com.fleetpilot.driver.data.api.ApiModels;
import com.fleetpilot.driver.data.local.OutboxItem;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class OutboxSenderTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private final Map<String, OutboxItem> store = new LinkedHashMap<>();
    private final List<String> records = new ArrayList<>();
    private final List<String> photos = new ArrayList<>();
    /** What the server answers, call after call: null is OK. */
    private final Deque<ApiException> answers = new LinkedList<>();
    private OutboxSender sender;

    @Before
    public void setUp() {
        sender = new OutboxSender(new OutboxSender.Store() {
            @Override
            public List<OutboxItem> pending() {
                return store.values().stream()
                        .filter(item -> item.errorCode == null)
                        .sorted(Comparator.comparingLong(item -> item.createdAt))
                        .toList();
            }

            @Override
            public void save(OutboxItem item) {
                store.put(item.id, item);
            }

            @Override
            public void delete(String id) {
                store.remove(id);
            }
        }, new OutboxSender.Transport() {
            @Override
            public void sendRecord(OutboxItem item) throws ApiException {
                answer();
                records.add(item.id);
            }

            @Override
            public void sendPhoto(OutboxItem item, String photoId, File file) throws ApiException {
                answer();
                photos.add(photoId);
            }
        });
    }

    private void answer() throws ApiException {
        ApiException next = answers.isEmpty() ? null : answers.poll();
        if (next != null) {
            throw next;
        }
    }

    private static ApiException networkDown() {
        return ApiException.network(new IOException("no route"));
    }

    private static ApiException refused() {
        ApiModels.Problem problem = new ApiModels.Problem();
        problem.code = "IMPLAUSIBLE_PRICE";
        problem.detail = "The price per litre is not plausible";
        return ApiException.http(422, problem);
    }

    private OutboxItem item(String id, long createdAt, String... photoIds) throws IOException {
        OutboxItem item = new OutboxItem();
        item.id = id;
        item.kind = OutboxItem.FUEL;
        item.createdAt = createdAt;
        List<String> paths = new ArrayList<>();
        for (String photoId : photoIds) {
            File file = folder.newFile(photoId + ".jpg");
            Files.write(file.toPath(), new byte[] {1, 2, 3});
            paths.add(file.getPath());
        }
        item.photoPaths = String.join("\n", paths);
        item.photoOwner = photoIds.length > 0 ? "incidents" : null;
        store.put(id, item);
        return item;
    }

    @Test
    public void sends_the_items_oldest_first_and_empties_the_outbox() throws IOException {
        item("second", 2000);
        item("first", 1000);

        OutboxSender.Report report = sender.flush();

        assertEquals(new OutboxSender.Report(2, 0, false), report);
        assertEquals(List.of("first", "second"), records);
        assertTrue(store.isEmpty());
    }

    @Test
    public void keeps_everything_and_stops_when_the_network_is_down() throws IOException {
        item("first", 1000);
        item("second", 2000);
        answers.add(networkDown());

        OutboxSender.Report report = sender.flush();

        assertTrue(report.interrupted());
        assertEquals(2, store.size());
        assertEquals(1, store.get("first").attempts);
        assertEquals(0, store.get("second").attempts);
        assertTrue(records.isEmpty());
    }

    @Test
    public void resumes_a_claim_at_the_next_photo_and_deletes_the_files_once_sent() throws IOException {
        OutboxItem claim = item("claim", 1000, "p1", "p2", "p3");
        answers.add(null);
        answers.add(null);
        answers.add(networkDown());

        sender.flush();

        OutboxItem halfway = store.get("claim");
        assertTrue(halfway.recordSent);
        assertEquals(List.of("p1"), halfway.sentPhotos());

        sender.flush();

        assertEquals(List.of("claim"), records);
        assertEquals(List.of("p1", "p2", "p3"), photos);
        assertTrue(store.isEmpty());
        for (String path : claim.photos()) {
            assertFalse(new File(path).exists());
        }
    }

    @Test
    public void sets_aside_a_refused_item_and_goes_on_with_the_next_ones() throws IOException {
        item("refused", 1000);
        item("fine", 2000);
        answers.add(refused());

        OutboxSender.Report report = sender.flush();

        assertEquals(new OutboxSender.Report(1, 1, false), report);
        assertEquals("IMPLAUSIBLE_PRICE", store.get("refused").errorCode);
        assertNull(store.get("fine"));

        sender.flush();
        assertEquals(List.of("fine"), records);
    }

    @Test
    public void the_server_errors_say_whether_to_retry() {
        assertTrue(networkDown().isTransient());
        assertTrue(ApiException.http(503, null).isTransient());
        assertTrue(ApiException.http(401, null).isTransient());
        assertFalse(refused().isTransient());
        assertEquals("HTTP_404", ApiException.http(404, null).code());
    }

    @Test
    public void the_id_of_a_photo_is_its_file_name() {
        assertEquals("3f1c", OutboxSender.photoId(new File("/data/outbox/3f1c.jpg")));
    }
}
