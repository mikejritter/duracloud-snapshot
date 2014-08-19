/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.service.impl;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.duracloud.common.notification.NotificationManager;
import org.duracloud.common.notification.NotificationType;
import org.duracloud.snapshot.common.SnapshotServiceConstants;
import org.duracloud.snapshot.db.ContentDirUtils;
import org.duracloud.snapshot.db.model.DuracloudEndPointConfig;
import org.duracloud.snapshot.db.model.Restoration;
import org.duracloud.snapshot.db.model.Snapshot;
import org.duracloud.snapshot.db.repo.RestoreRepo;
import org.duracloud.snapshot.dto.RestoreStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.JobParameters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Erik Paulsson
 *         Date: 2/10/14
 */
@Component("restoreJobListener")
public class RestoreJobExecutionListener implements JobExecutionListener {

    private static final Logger log =
        LoggerFactory.getLogger(SnapshotJobExecutionListener.class);

    @Autowired
    private NotificationManager notificationManager;
    
    @Autowired
    private RestoreRepo restoreRepo;
    
    private ExecutionListenerConfig config;
    
    
    /**
     * @param notificationManager the notificationManager to set
     */
    public void setNotificationManager(NotificationManager notificationManager) {
        this.notificationManager = notificationManager;
    }
    
    
    /**
     * @param restoreRepo the restoreRepo to set
     */
    public void setRestorationRepo(RestoreRepo restoreRepo) {
        this.restoreRepo = restoreRepo;
    }
    

    public void init(ExecutionListenerConfig config) {
        this.config = config;
    }

    @Transactional
    public void beforeJob(JobExecution jobExecution) {
    }

    @Transactional
    public void afterJob(JobExecution jobExecution) {
        JobParameters jobParams = jobExecution.getJobParameters();
        BatchStatus status = jobExecution.getStatus();
        Long objectId = jobParams.getLong(SnapshotServiceConstants.OBJECT_ID);
        Restoration restoration = restoreRepo.findOne(objectId);
        String restorationPath = ContentDirUtils.getSourcePath(restoration.getId(), config.getContentRoot());
        log.debug("Completed restoration: {} with status: {}", restoration.getId(), status);

        Snapshot snapshot = restoration.getSnapshot();
        String snapshotId = snapshot.getName();
        Long restoreId = restoration.getId();

        if(BatchStatus.COMPLETED.equals(status)) {
            // Job success. Email duracloud team as well as restoration requestor
            
            changeRestoreStatus(restoration,
                                RestoreStatus.RESTORATION_COMPLETE,
                                "Completed transfer to duracloud: "
                                    + new Date());
            String subject =
                "DuraCloud snapshot " + snapshotId + " has been restored! Restore ID = " + restoreId;
            String message =
                "A DuraCloud snapshot restore  has completed successfully:\n\n";
            
            DuracloudEndPointConfig destination = restoration.getDestination();
            message += "SnapshotId: " + snapshotId + "\n";
            message += "Restore Id: " + restoreId + "\n";
            message += "Destination Host: " + destination.getHost() + "\n";
            message += "Destination Port: " + destination.getPort() + "\n";
            message += "Destination StoreId: " + destination.getStoreId() + "\n";
            message += "Destination SpaceId: " + destination.getSpaceId() + "\n";
            
            log.info("deleting restoration path " + restorationPath);
            
            try {
                FileUtils.deleteDirectory(new File(restorationPath));
            } catch (IOException e) {
                log.error("failed to delete restoration path = "
                    + restorationPath + ": " + e.getMessage(), e);
            }
            
            List<String> emailAddresses =
                new ArrayList<>(Arrays.asList(config.getDuracloudEmailAddresses()));
            emailAddresses.add(restoration.getUserEmail());
            sendEmail(subject, message, emailAddresses.toArray(new String[0]));
            

        } else {
            changeRestoreStatus(restoration,
                                RestoreStatus.ERROR,
                                "failed to transfer to duracloud: "
                                    + new Date());

            // Job failed.  Email DuraSpace team about failed snapshot attempt.
            String subject =
                "DuraCloud snapshot "+ snapshotId + " restoration failed to complete";
            String message =
                "A DuraCloud snapshot restoration has failed to complete.\n" +
                "\nrrestore-id=" + restoreId +
                "\nsnapshot-id=" + snapshotId +
                "\nrestore-path=" + restorationPath;
                // TODO: Add details of failure in message
            sendEmail(subject, message,
                      config.getDuracloudEmailAddresses());

        }
    }



    /**
     * @param restoration
     * @param status 
     * @param msg
     */
    private void changeRestoreStatus(Restoration restoration,
                                      RestoreStatus status, String msg) {
        restoration.setStatus(status);
        restoration.setStatusText(msg);
        if(status.equals(RestoreStatus.RESTORATION_COMPLETE)) {
            restoration.setEndDate(new Date());
        }
        restoreRepo.save(restoration);
    }

    private void sendEmail(String subject, String msg, String... destinations) {
        notificationManager.sendNotification(NotificationType.EMAIL,
                                             subject,
                                             msg.toString(),
                                             destinations);
    }
}