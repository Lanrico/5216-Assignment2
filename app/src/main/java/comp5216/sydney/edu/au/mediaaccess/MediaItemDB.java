package comp5216.sydney.edu.au.mediaaccess;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {MediaItem.class}, version = 2, exportSchema = false)
public abstract class MediaItemDB extends RoomDatabase {
    private static final String DATABASE_NAME = "media_item_db";
    private static MediaItemDB DBINSTANCE;

    public abstract MediaItemDao mediaItemDao();

    public static MediaItemDB getDatabase(Context context) {
        if (DBINSTANCE == null) {
            synchronized (MediaItemDB.class) {
                DBINSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                        MediaItemDB.class, DATABASE_NAME).build();
            }
        }
        return DBINSTANCE;
    }
}