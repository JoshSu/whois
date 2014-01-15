package net.ripe.db.whois.scheduler.task.grs;

import net.ripe.db.whois.common.IntegrationTest;
import net.ripe.db.whois.common.dao.jdbc.DatabaseHelper;
import net.ripe.db.whois.common.rpsl.RpslObject;
import net.ripe.db.whois.scheduler.AbstractSchedulerIntegrationTest;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

@DirtiesContext
@Category(IntegrationTest.class)
public class GrsImporterTestIntegration extends AbstractSchedulerIntegrationTest {
    @Autowired
    GrsImporter grsImporter;

    @BeforeClass
    public static void setup_database() {
        DatabaseHelper.addGrsDatabases("RIPE-GRS");
    }

    @AfterClass
    public static void reset_property() {
        System.clearProperty("grs.sources");
    }

    @Before
    public void setUp() throws Exception {
        grsImporter.setGrsImportEnabled(true);
    }

    @Test
    public void run_ripe_grs() throws Exception {
        databaseHelper.addObject("" +
                "mntner:         SOME-MNT\n" +
                "descr:          description\n" +
                "mnt-by:         SOME-MNT\n" +
                "referral-by:    SOME-MNT\n" +
                "upd-to:         dbtest@ripe.net\n" +
                "auth:           MD5-PW $1$fU9ZMQN9$QQtm3kRqZXWAuLpeOiLN7. # update\n" +
                "changed:        dbtest@ripe.net 20120707\n" +
                "source:         TEST\n"
        );

        final RpslObject maintainer = RpslObject.parse("" +
                "mntner:         UPD-MNT\n" +
                "descr:          description\n" +
                "mnt-by:         UPD-MNT\n" +
                "referral-by:    UPD-MNT\n" +
                "upd-to:         dbtest@ripe.net\n" +
                "auth:           MD5-PW $1$fU9ZMQN9$QQtm3kRqZXWAuLpeOiLN7. # update\n" +
                "changed:        dbtest@ripe.net 20120707\n" +
                "source:         TEST\n"
        );

        databaseHelper.addObject(maintainer);
        awaitAll(grsImporter.grsImport("RIPE-GRS", false));

        databaseHelper.deleteObject(maintainer);
        awaitAll(grsImporter.grsImport("RIPE-GRS", false));
    }

    private void awaitAll(final List<Future> futures) throws ExecutionException, InterruptedException {
        for (final Future<?> future : futures) {
            future.get();
        }
    }
}
