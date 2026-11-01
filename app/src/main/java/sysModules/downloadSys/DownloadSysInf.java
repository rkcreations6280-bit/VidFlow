package sysModules.downloadSys;

import dataRepo.downloads.DownloadInfo;
import java.util.List;

public interface DownloadSysInf {
    boolean isDownloadSystemInitialize();
    List<DownloadInfo> getAllDownloadRecordsFromRepo();
    List<DownloadInfo> getAllFinishedDownloadRecordsFromRepo();
}
