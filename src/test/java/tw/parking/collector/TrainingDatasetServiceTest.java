package tw.parking.collector;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TrainingDatasetServiceTest {

    @Test
    void roadsideLabelMarksOnlyConfirmedAvailableStatusAsTrue() {
        assertThat(TrainingDatasetService.roadsideLabel("2"))
                .isEqualTo(new TrainingDatasetService.RoadsideLabel("true", "AVAILABLE", ""));

        assertThat(TrainingDatasetService.roadsideLabel("1"))
                .isEqualTo(new TrainingDatasetService.RoadsideLabel("false", "OCCUPIED", "occupied_status"));

        assertThat(TrainingDatasetService.roadsideLabel("3"))
                .isEqualTo(new TrainingDatasetService.RoadsideLabel(
                        "false",
                        "RESTRICTED_OR_CLOSED",
                        "restricted_or_closed_status"));

        assertThat(TrainingDatasetService.roadsideLabel("5"))
                .isEqualTo(new TrainingDatasetService.RoadsideLabel(
                        "false",
                        "UNKNOWN_OR_SPECIAL",
                        "unknown_or_special_status"));

        assertThat(TrainingDatasetService.roadsideLabel("0"))
                .isEqualTo(new TrainingDatasetService.RoadsideLabel(
                        "false",
                        "UNKNOWN",
                        "unverified_parkingstatus"));
    }
}
