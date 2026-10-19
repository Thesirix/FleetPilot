package com.fleetpilot.driver.notifications;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import com.fleetpilot.driver.data.api.ApiModels.DriverNotification;
import com.fleetpilot.driver.data.local.NotificationState;
import org.junit.Test;

public class NotificationsWorkerTest {

    private static DriverNotification notification(String id) {
        DriverNotification n = new DriverNotification();
        n.id = id;
        return n;
    }

    private static NotificationState state(String id, boolean announced, boolean seen) {
        NotificationState state = new NotificationState();
        state.id = id;
        state.announced = announced;
        state.seen = seen;
        return state;
    }

    @Test
    public void announces_only_what_the_phone_has_never_shown() {
        List<DriverNotification> list = List.of(notification("deadline:1:OVERDUE"), notification("alert:2"),
                notification("incident:3:IN_REPAIR"));
        List<NotificationState> known = List.of(state("alert:2", true, false), state("incident:3:IN_REPAIR", false, true));

        List<DriverNotification> fresh = NotificationsWorker.toAnnounce(list, known);

        assertEquals(1, fresh.size());
        assertEquals("deadline:1:OVERDUE", fresh.get(0).id);
    }

    @Test
    public void a_deadline_that_becomes_overdue_is_announced_again() {
        // Same deadline, new id: it was only due soon when it was announced
        List<DriverNotification> fresh = NotificationsWorker.toAnnounce(List.of(notification("deadline:1:OVERDUE")),
                List.of(state("deadline:1:DUE_SOON", true, true)));
        assertEquals(1, fresh.size());
    }

    @Test
    public void at_most_three_at_once_most_serious_first() {
        List<DriverNotification> list = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            list.add(notification("alert:" + i));
        }
        List<DriverNotification> fresh = NotificationsWorker.toAnnounce(list, List.of());
        assertEquals(NotificationsWorker.MAX_AT_ONCE, fresh.size());
        assertEquals("alert:0", fresh.get(0).id);
    }
}
