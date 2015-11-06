/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.bridge.rest;


import java.util.Date;

import javax.ws.rs.core.Response;

import org.codehaus.jettison.json.JSONException;
import org.duracloud.common.util.DateUtil;
import org.duracloud.snapshot.SnapshotException;
import org.duracloud.snapshot.bridge.rest.RestoreResource;
import org.duracloud.snapshot.common.test.SnapshotTestBase;
import org.duracloud.snapshot.db.model.DuracloudEndPointConfig;
import org.duracloud.snapshot.db.model.Restoration;
import org.duracloud.snapshot.db.model.Snapshot;
import org.duracloud.snapshot.dto.RestoreStatus;
import org.duracloud.snapshot.dto.bridge.CreateRestoreBridgeParameters;
import org.duracloud.snapshot.dto.bridge.RequestRestoreBridgeParameters;
import org.duracloud.snapshot.service.RestoreManager;
import org.duracloud.snapshot.service.SnapshotManager;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.easymock.Mock;
import org.easymock.TestSubject;
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Daniel Bernstein
 *         Date: Feb 4, 2014
 */

public class RestoreResourceTest extends SnapshotTestBase {
    
    @Mock
    private RestoreManager manager;

    @Mock
    private SnapshotManager snapshotManager;

    @Mock
    private Restoration restoration;

    @Mock
    private Snapshot snapshot;

    @TestSubject
    private RestoreResource resource;
    
    /* (non-Javadoc)
     * @see org.duracloud.snapshot.common.test.EasyMockTestBase#setup()
     */
    @Override
    public void setup() throws Exception {
        super.setup();
        resource = new RestoreResource(manager, snapshotManager);
    }
    
    @Test
    public void testRestoreSnapshot() throws SnapshotException {
        String restorationId = "restoration-id";
        String userEmail = "user-email";
        
        EasyMock.expect(manager.restoreSnapshot(EasyMock.isA(String.class),
                                                EasyMock.isA(DuracloudEndPointConfig.class),
                                                EasyMock.isA(String.class)))
                .andReturn(restoration);

        EasyMock.expect(restoration.getSnapshot())
                .andReturn(snapshot);
        EasyMock.expect(restoration.getRestorationId())
                .andReturn(restorationId).times(2);
        EasyMock.expect(restoration.getStatus())
                .andReturn(RestoreStatus.INITIALIZED);

        Capture<String> historyCapture = new Capture<>();
        EasyMock.expect(snapshotManager.updateHistory(EasyMock.isA(Snapshot.class),
                                                      EasyMock.capture(historyCapture)))
                .andReturn(snapshot);

        replayAll();

        CreateRestoreBridgeParameters params = new CreateRestoreBridgeParameters();
        params.setHost("host");
        params.setPort("443");
        params.setSnapshotId("snapshot");
        params.setSpaceId("space");
        params.setUserEmail(userEmail);
        resource.restoreSnapshot(params);

        String history = historyCapture.getValue();
        String expectedHistory =
            "[{'restore-action':'RESTORE_INITIATED'}," +
             "{'restore-id':'"+restorationId+"'}," +
             "{'initiating-user':'"+userEmail+"'}]";
        assertEquals(expectedHistory, history.replaceAll("\\s", ""));
    }

    @Test
    public void testRequesetRestoreSnapshot() throws SnapshotException {
        String restorationId = "restoration-id";
        String userEmail = "user-email";

        EasyMock.expect(
            manager.requestRestoreSnapshot(EasyMock.isA(String.class),
                                           EasyMock.isA(DuracloudEndPointConfig.class),
                                           EasyMock.isA(String.class)))
                .andReturn(snapshot);

        Capture<String> historyCapture = new Capture<>();
        EasyMock.expect(snapshotManager.updateHistory(EasyMock.isA(Snapshot.class),
                                                      EasyMock.capture(historyCapture)))
                .andReturn(snapshot);

        replayAll();

        RequestRestoreBridgeParameters params = new RequestRestoreBridgeParameters();
        params.setHost("host");
        params.setPort("443");
        params.setSnapshotId("snapshot");
        params.setSpaceId("space");
        params.setUserEmail(userEmail);
        resource.requestRestoreSnapshot(params);

        String history = historyCapture.getValue();
        String expectedHistory =
            "[{'restore-action':'RESTORE_REQUESTED'}," +
             "{'initiating-user':'"+userEmail+"'}]";
        assertEquals(expectedHistory, history.replaceAll("\\s", ""));
    }

    @Test
    public void testSnapshotRestorationComplete() throws SnapshotException {
        String restorationId = "restoration-id";
        Date expirationDate = new Date();

        EasyMock.expect(manager.restoreCompleted(restorationId))
                .andReturn(restoration);

        EasyMock.expect(restoration.getStatus())
                .andReturn(RestoreStatus.RESTORATION_COMPLETE);
        EasyMock.expect(restoration.getStatusText())
                .andReturn("Status text");

        replayAll();
        resource.restoreComplete(restorationId);
    }

    @Test
    public void testGetRestore() throws SnapshotException, JSONException {
        String restorationId = "restoration-id";
        Restoration restoration = setupRestoration();
        EasyMock.expect(manager.get(restorationId)).andReturn(restoration);
        replayAll();
        Response response = resource.get(restorationId);
        Assert.assertNotNull(response);
        Assert.assertNotNull(response.getEntity());
    }
    
    @Test
    public void testGetRestoreBySnapshot() throws SnapshotException, JSONException {
        String snapshotId = "snapshot-id";
        Restoration restoration = setupRestoration();
        EasyMock.expect(manager.getBySnapshotId(snapshotId)).andReturn(restoration);
        replayAll();
        Response response = resource.getBySnapshot(snapshotId);
        Assert.assertNotNull(response);
        Assert.assertNotNull(response.getEntity());
    }

    /**
     * @return
     */
    private Restoration setupRestoration() {
        Restoration r = createMock(Restoration.class);
        EasyMock.expect(r.getRestorationId()).andReturn("restore-id");
        EasyMock.expect(r.getStatus()).andReturn(RestoreStatus.DPN_TRANSFER_COMPLETE);
        Snapshot snapshot = createMock(Snapshot.class);
        EasyMock.expect(r.getSnapshot()).andReturn(snapshot);
        EasyMock.expect(snapshot.getName()).andReturn("snapshot-id");
        EasyMock.expect(r.getStartDate()).andReturn(new Date());
        EasyMock.expect(r.getEndDate()).andReturn(new Date());
        EasyMock.expect(r.getExpirationDate()).andReturn(new Date());
        EasyMock.expect(r.getStatusText()).andReturn("status text");
        DuracloudEndPointConfig dest = createMock(DuracloudEndPointConfig.class);
        EasyMock.expect(r.getDestination()).andReturn(dest);
        EasyMock.expect(dest.getHost()).andReturn("host");
        EasyMock.expect(dest.getPort()).andReturn(443);
        EasyMock.expect(dest.getStoreId()).andReturn("store-id");
        EasyMock.expect(dest.getSpaceId()).andReturn("space-id");
        
        return r;
    }

}
