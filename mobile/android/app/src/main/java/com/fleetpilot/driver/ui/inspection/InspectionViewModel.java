package com.fleetpilot.driver.ui.inspection;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import com.fleetpilot.driver.R;
import com.fleetpilot.driver.data.api.ApiModels.CheckAnswer;
import com.fleetpilot.driver.data.api.ApiModels.DriverSpace;
import com.fleetpilot.driver.data.api.ApiModels.Inspection;
import com.fleetpilot.driver.data.api.ApiModels.RecordInspection;
import com.fleetpilot.driver.data.local.OutboxItem;
import com.fleetpilot.driver.domain.Checklist;
import com.fleetpilot.driver.ui.DriverViewModel;
import com.fleetpilot.driver.util.Formats;
import com.fleetpilot.driver.util.Labels;
import com.fleetpilot.driver.util.Resource;

/** The checklist being filled in survives a rotation of the screen; once sent, it starts again. */
public class InspectionViewModel extends DriverViewModel {

    private Checklist checklist = new Checklist();

    public InspectionViewModel(@NonNull Application application) {
        super(application);
    }

    public Checklist checklist() {
        return checklist;
    }

    public LiveData<Resource<DriverSpace>> space() {
        return repository.space();
    }

    public LiveData<Resource<List<Inspection>>> history() {
        return repository.inspections();
    }

    public LiveData<List<OutboxItem>> outbox() {
        return repository.outbox();
    }

    public void refresh() {
        repository.refreshSpace();
        repository.refreshInspections();
    }

    public void save(String kind, int mileage, int fuelLevel, String remarks, List<File> photos) {
        RecordInspection inspection = new RecordInspection();
        inspection.kind = kind;
        inspection.performedAt = Formats.now();
        inspection.mileage = mileage;
        inspection.fuelLevel = fuelLevel;
        inspection.remarks = remarks == null || remarks.isBlank() ? null : remarks.trim();
        List<CheckAnswer> answers = new ArrayList<>();
        for (String item : Checklist.ITEMS) {
            Checklist.Answer answer = checklist.get(item);
            String comment = answer.comment().isBlank() ? null : answer.comment().trim();
            answers.add(new CheckAnswer(item, answer.condition(), comment));
        }
        inspection.checks = answers;
        String summary = getApplication().getResources().getQuantityString(R.plurals.outbox_inspection,
                checklist.defects().size(), Labels.label(getApplication(), "inspection_kind", kind), checklist.defects().size());
        OutboxItem item = item(OutboxItem.INSPECTION, summary, inspection);
        item.photoOwner = "inspections";
        item.photoPaths = String.join("\n", photos.stream().map(File::getPath).collect(Collectors.toList()));
        submit(item);
        checklist = new Checklist();
    }
}
