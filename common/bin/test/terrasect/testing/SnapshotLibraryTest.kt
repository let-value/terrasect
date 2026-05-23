package terrasect.testing

import de.skuzzle.test.snapshots.Snapshot
import de.skuzzle.test.snapshots.junit5.EnableSnapshotTests
import org.junit.jupiter.api.Test

@EnableSnapshotTests
class SnapshotLibraryTest {

  @Test
  fun storesSnapshotInDefaultDirectory(snapshot: Snapshot) {
    snapshot.assertThat("hello snapshot").asText().matchesSnapshotText()
  }
}
