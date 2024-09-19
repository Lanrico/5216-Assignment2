package comp5216.sydney.edu.au.mediaaccess;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "MediaItemList")
public class MediaItem {
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "ID")
    private int ID;

    @ColumnInfo(name = "fileName")
    private String fileName;

    @ColumnInfo(name = "localPath")
    private String localPath;

    @ColumnInfo(name = "fileType")
    private String fileType;

    @ColumnInfo(name = "city")
    private String city;

    @ColumnInfo(name = "isBackup")
    private boolean isBackup;

    public MediaItem(
            String fileName,
            String localPath,
            String fileType,
            String city,
            boolean isBackup
    ) {
        this.fileName = fileName;
        this.localPath = localPath;
        this.fileType = fileType;
        this.city = city;
        this.isBackup = isBackup;
    }

    public int getID() {
        return ID;
    }

    public void setID(int ID) {
        this.ID = ID;
    }

    public String getFileName() {
        return fileName;
    }

    public String getLocalPath() {
        return localPath;
    }

    public String getFileType() {
        return fileType;
    }

    public String getCity() {
        return city;
    }

    public boolean isBackup() {
        return isBackup;
    }

    public void setBackup(boolean backup) {
        isBackup = backup;
    }
}