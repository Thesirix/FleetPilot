package com.fleetpilot.driver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.List;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.fleetpilot.driver.data.local.AppDatabase;
import com.fleetpilot.driver.data.local.OutboxDao;
import com.fleetpilot.driver.data.local.OutboxItem;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** The outbox in a real SQLite database of the phone (Room, in memory). */
@RunWith(AndroidJUnit4.class)
public class OutboxDaoTest {

    private AppDatabase db;
    private OutboxDao dao;

    @Before
    public void open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        dao = db.outbox();
    }

    @After
    public void close() {
        db.close();
    }

    private static OutboxItem item(String id, long createdAt) {
        OutboxItem item = new OutboxItem();
        item.id = id;
        item.kind = OutboxItem.FUEL;
        item.summary = "Plein " + id;
        item.payload = "{}";
        item.createdAt = createdAt;
        return item;
    }

    @Test
    public void the_waiting_items_come_oldest_first_and_a_refused_one_is_set_aside() {
        dao.save(item("b", 2));
        dao.save(item("a", 1));
        OutboxItem refused = item("c", 3);
        refused.errorCode = "IMPLAUSIBLE_PRICE";
        dao.save(refused);

        List<OutboxItem> pending = dao.pending();

        assertEquals(List.of("a", "b"), pending.stream().map(i -> i.id).toList());
    }

    @Test
    public void the_progress_of_a_claim_survives_a_restart_of_the_app() {
        OutboxItem claim = item("claim", 1);
        claim.photoPaths = "/data/outbox/p1.jpg\n/data/outbox/p2.jpg";
        dao.save(claim);
        claim.recordSent = true;
        claim.markPhotoSent("p1");
        dao.save(claim);

        OutboxItem read = dao.find("claim");
        assertEquals(List.of("p1"), read.sentPhotos());
        assertEquals(2, read.photos().size());

        dao.delete("claim");
        assertNull(dao.find("claim"));
    }
}
