package comp5216.sydney.edu.au.mediaaccess;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface MediaItemDao {
    @Insert
    void insert(MediaItem mediaItem);

    @Query("SELECT * FROM MediaItemList where isBackup = 0")
    List<MediaItem> listAllNotBackup();

    // Change isBackup to 1
    @Query("UPDATE MediaItemList SET isBackup = 1 WHERE ID = :ID")
    void updateBackup(int ID);
}
