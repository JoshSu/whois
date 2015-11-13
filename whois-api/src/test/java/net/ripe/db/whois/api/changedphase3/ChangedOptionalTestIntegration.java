package net.ripe.db.whois.api.changedphase3;

import net.ripe.db.whois.common.IntegrationTest;
import net.ripe.db.whois.common.rpsl.RpslObject;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;


import static net.ripe.db.whois.api.changedphase3.Scenario.Builder.given;
import static net.ripe.db.whois.api.changedphase3.Scenario.Method.CREATE;
import static net.ripe.db.whois.api.changedphase3.Scenario.Method.DELETE;
import static net.ripe.db.whois.api.changedphase3.Scenario.Method.EVENT_;
import static net.ripe.db.whois.api.changedphase3.Scenario.Method.GET___;
import static net.ripe.db.whois.api.changedphase3.Scenario.Method.META__;
import static net.ripe.db.whois.api.changedphase3.Scenario.Method.MODIFY;
import static net.ripe.db.whois.api.changedphase3.Scenario.Method.SEARCH;
import static net.ripe.db.whois.api.changedphase3.Scenario.Mode.OLD_MODE;
import static net.ripe.db.whois.api.changedphase3.Scenario.ObjectStatus.OBJ_DOES_NOT_EXIST_____;
import static net.ripe.db.whois.api.changedphase3.Scenario.ObjectStatus.OBJ_EXISTS_NO_CHANGED__;
import static net.ripe.db.whois.api.changedphase3.Scenario.ObjectStatus.OBJ_EXISTS_WITH_CHANGED;
import static net.ripe.db.whois.api.changedphase3.Scenario.Protocol.MAILUPD;
import static net.ripe.db.whois.api.changedphase3.Scenario.Protocol.NRTM___;
import static net.ripe.db.whois.api.changedphase3.Scenario.Protocol.REST___;
import static net.ripe.db.whois.api.changedphase3.Scenario.Protocol.SYNCUPD;
import static net.ripe.db.whois.api.changedphase3.Scenario.Protocol.TELNET_;
import static net.ripe.db.whois.api.changedphase3.Scenario.Req.NOT_APPLIC__;
import static net.ripe.db.whois.api.changedphase3.Scenario.Req.NO_CHANGED__;
import static net.ripe.db.whois.api.changedphase3.Scenario.Req.WITH_CHANGED;
import static net.ripe.db.whois.api.changedphase3.Scenario.Result.FAILED;
import static net.ripe.db.whois.api.changedphase3.Scenario.Result.SUCCESS;


@Category(IntegrationTest.class)
public class ChangedOptionalTestIntegration extends AbstractChangedPhase3IntegrationTest {

    @BeforeClass
    public static void beforeClass() {
        System.setProperty("feature.toggle.changed.attr.available", "true");
    }

    @AfterClass
    public static void afterClass() {
        System.clearProperty("feature.toggle.changed.attr.available");
    }

    @Test
    public void rest_create_with_changed_old_mode() throws Exception {
        verifyObjectDoesntExist();

        final RpslObject output = restCreateObject(PERSON_WITH_CHANGED());

        verifyResponse(output, MUST_CONTAIN_CHANGED);
        verifyMail(MUST_CONTAIN_CHANGED);
    }

    @Test
    public void rest_create_without_changed_old_mode() throws Exception {
        verifyObjectDoesntExist();

        final RpslObject output = restCreateObject(PERSON_WITHOUT_CHANGED());

        verifyResponse(output, MUST_NOT_CONTAIN_CHANGED);
        verifyMail(MUST_NOT_CONTAIN_CHANGED);
    }


    @Test
    public void old_mode_rest_test() {
        given(OLD_MODE, OBJ_DOES_NOT_EXIST_____).when(REST___, CREATE, WITH_CHANGED).then(SUCCESS, OBJ_EXISTS_WITH_CHANGED).run();
        given(OLD_MODE, OBJ_DOES_NOT_EXIST_____).when(REST___, CREATE, NO_CHANGED__).then(SUCCESS, OBJ_EXISTS_NO_CHANGED__).run();

        given(OLD_MODE, OBJ_EXISTS_WITH_CHANGED).when(REST___, MODIFY, WITH_CHANGED).then(SUCCESS, OBJ_EXISTS_WITH_CHANGED).run();
        given(OLD_MODE, OBJ_EXISTS_WITH_CHANGED).when(REST___, MODIFY, NO_CHANGED__).then(SUCCESS, OBJ_EXISTS_NO_CHANGED__).run();
        given(OLD_MODE, OBJ_EXISTS_NO_CHANGED__).when(REST___, MODIFY, WITH_CHANGED).then(SUCCESS, OBJ_EXISTS_WITH_CHANGED).run();
        given(OLD_MODE, OBJ_EXISTS_NO_CHANGED__).when(REST___, MODIFY, NO_CHANGED__).then(SUCCESS, OBJ_EXISTS_NO_CHANGED__).run();

        given(OLD_MODE, OBJ_EXISTS_WITH_CHANGED).when(REST___, DELETE, NOT_APPLIC__).then(SUCCESS, OBJ_DOES_NOT_EXIST_____).run();
        given(OLD_MODE, OBJ_EXISTS_NO_CHANGED__).when(REST___, DELETE, NOT_APPLIC__).then(SUCCESS, OBJ_DOES_NOT_EXIST_____).run();

        given(OLD_MODE, OBJ_EXISTS_WITH_CHANGED).when(REST___, SEARCH, NOT_APPLIC__).then(SUCCESS, OBJ_EXISTS_WITH_CHANGED).run();
        given(OLD_MODE, OBJ_EXISTS_NO_CHANGED__).when(REST___, SEARCH, NOT_APPLIC__).then(SUCCESS, OBJ_EXISTS_NO_CHANGED__).run();

        given(OLD_MODE, OBJ_EXISTS_WITH_CHANGED).when(REST___, GET___, NOT_APPLIC__).then(SUCCESS, OBJ_EXISTS_WITH_CHANGED).run();
        given(OLD_MODE, OBJ_EXISTS_NO_CHANGED__).when(REST___, GET___, NOT_APPLIC__).then(SUCCESS, OBJ_EXISTS_NO_CHANGED__).run();

        given(OLD_MODE, OBJ_DOES_NOT_EXIST_____).when(REST___, META__, NOT_APPLIC__).then(SUCCESS, OBJ_EXISTS_WITH_CHANGED).run();
    }

    @Test
    public void old_mode_telnet_test() {
        given(OLD_MODE, OBJ_EXISTS_WITH_CHANGED).when(TELNET_, SEARCH, NOT_APPLIC__).then(SUCCESS, OBJ_EXISTS_WITH_CHANGED).run();
        given(OLD_MODE, OBJ_EXISTS_NO_CHANGED__).when(TELNET_, SEARCH, NOT_APPLIC__).then(SUCCESS, OBJ_EXISTS_NO_CHANGED__).run();

        given(OLD_MODE, OBJ_DOES_NOT_EXIST_____).when(TELNET_, META__, NOT_APPLIC__).then(SUCCESS, OBJ_EXISTS_WITH_CHANGED).run();
    }

    @Test
    public void old_mode_syncupdates_test() {
        given(OLD_MODE, OBJ_DOES_NOT_EXIST_____).when(SYNCUPD, CREATE, WITH_CHANGED).then(SUCCESS, OBJ_EXISTS_WITH_CHANGED).run();
        given(OLD_MODE, OBJ_DOES_NOT_EXIST_____).when(SYNCUPD, CREATE, NO_CHANGED__).then(SUCCESS, OBJ_EXISTS_NO_CHANGED__).run();

        given(OLD_MODE, OBJ_EXISTS_WITH_CHANGED).when(SYNCUPD, MODIFY, WITH_CHANGED).then(SUCCESS, OBJ_EXISTS_WITH_CHANGED).run();
        given(OLD_MODE, OBJ_EXISTS_WITH_CHANGED).when(SYNCUPD, MODIFY, NO_CHANGED__).then(SUCCESS, OBJ_EXISTS_NO_CHANGED__).run();
        given(OLD_MODE, OBJ_EXISTS_NO_CHANGED__).when(SYNCUPD, MODIFY, WITH_CHANGED).then(SUCCESS, OBJ_EXISTS_WITH_CHANGED).run();
        given(OLD_MODE, OBJ_EXISTS_NO_CHANGED__).when(SYNCUPD, MODIFY, NO_CHANGED__).then(SUCCESS, OBJ_EXISTS_NO_CHANGED__).run();

        given(OLD_MODE, OBJ_EXISTS_WITH_CHANGED).when(SYNCUPD, DELETE, WITH_CHANGED).then(SUCCESS, OBJ_DOES_NOT_EXIST_____).run();
        given(OLD_MODE, OBJ_EXISTS_WITH_CHANGED).when(SYNCUPD, DELETE, NO_CHANGED__).then(FAILED).run();
        given(OLD_MODE, OBJ_EXISTS_NO_CHANGED__).when(SYNCUPD, DELETE, WITH_CHANGED).then(FAILED).run();
        given(OLD_MODE, OBJ_EXISTS_NO_CHANGED__).when(SYNCUPD, DELETE, NO_CHANGED__).then(SUCCESS, OBJ_DOES_NOT_EXIST_____).run();
    }

    @Test
    public void old_mode_mailupdates_test() {
        given(OLD_MODE, OBJ_DOES_NOT_EXIST_____).when(MAILUPD, CREATE, WITH_CHANGED).then(SUCCESS, OBJ_EXISTS_WITH_CHANGED).run();
        given(OLD_MODE, OBJ_DOES_NOT_EXIST_____).when(MAILUPD, CREATE, NO_CHANGED__).then(SUCCESS, OBJ_EXISTS_NO_CHANGED__).run();

        given(OLD_MODE, OBJ_EXISTS_WITH_CHANGED).when(MAILUPD, MODIFY, WITH_CHANGED).then(SUCCESS, OBJ_EXISTS_WITH_CHANGED).run();
        given(OLD_MODE, OBJ_EXISTS_WITH_CHANGED).when(MAILUPD, MODIFY, NO_CHANGED__).then(SUCCESS, OBJ_EXISTS_NO_CHANGED__).run();
        given(OLD_MODE, OBJ_EXISTS_NO_CHANGED__).when(MAILUPD, MODIFY, WITH_CHANGED).then(SUCCESS, OBJ_EXISTS_WITH_CHANGED).run();
        given(OLD_MODE, OBJ_EXISTS_NO_CHANGED__).when(MAILUPD, MODIFY, NO_CHANGED__).then(SUCCESS, OBJ_EXISTS_NO_CHANGED__).run();

        given(OLD_MODE, OBJ_EXISTS_WITH_CHANGED).when(MAILUPD, DELETE, WITH_CHANGED).then(SUCCESS, OBJ_DOES_NOT_EXIST_____).run();
        given(OLD_MODE, OBJ_EXISTS_WITH_CHANGED).when(MAILUPD, DELETE, NO_CHANGED__).then(FAILED).run();
        given(OLD_MODE, OBJ_EXISTS_NO_CHANGED__).when(MAILUPD, DELETE, WITH_CHANGED).then(FAILED).run();
        given(OLD_MODE, OBJ_EXISTS_NO_CHANGED__).when(MAILUPD, DELETE, NO_CHANGED__).then(SUCCESS, OBJ_DOES_NOT_EXIST_____).run();

    }

    @Test
    public void old_mode_nrtm_test() {
        given(OLD_MODE, OBJ_EXISTS_WITH_CHANGED).when(NRTM___, EVENT_, NOT_APPLIC__).then(SUCCESS, OBJ_EXISTS_WITH_CHANGED).run();
        given(OLD_MODE, OBJ_EXISTS_NO_CHANGED__).when(NRTM___, EVENT_, NOT_APPLIC__).then(SUCCESS, OBJ_EXISTS_NO_CHANGED__).run();
    }

}
