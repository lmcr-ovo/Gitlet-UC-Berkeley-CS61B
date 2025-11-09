package gitlet;

import java.io.*;

import static gitlet.Repository.*;
import static gitlet.Utils.join;
import static gitlet.Utils.readObject;

public class MyUtils {
    public static boolean deleteFile(File file) {
        if (!file.isDirectory()) {
            if (file.exists()) {
                return file.delete();
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    public static void deleteAllFiles(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return;
        }

        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }

        for (File f : files) {
            if (f.isFile()) {
                deleteFile(f);
            }
        }
    }

    /**
     * read Blob or Commit object from objets dir
     */
    public static Serializable readSerializable(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }

        File targetFile;

        // 1. full 40‑character commit ID -> direct read
        if (id.length() == LONG_COMMIT_SIZE) {
            targetFile = join(OBJECT_DIR, id);
            if (!targetFile.exists()) {
                return null;
            }
        } else if (id.length() == SHORT_COMMIT_SIZE) {
            // 2. short 8‑character prefix -> search for match
            String prefix = id;
            File[] files = OBJECT_DIR.listFiles();
            if (files == null) {
                return null;
            }

            File matched = null;
            for (File f : files) {
                if (f.getName().startsWith(prefix)) {
                    if (matched != null) {
                        // more than one file matches -> ambiguous
                        System.out.println("Ambiguous short ID: "
                                + "multiple objects share prefix " + prefix);
                        return null;
                    }
                    matched = f;
                }
            }
            if (matched == null) {
                System.out.println("No object found with prefix: " + prefix);
                return null;
            }
            targetFile = matched;
        } else {
            // not 8 or 40 characters -> invalid ID format
            System.out.println("Invalid ID format: " + id);
            return null;
        }

        // Try reading as Commit first, then Blob as fallback
        Serializable obj = readObject(targetFile, Serializable.class);
        if (obj instanceof Commit) {
            return (Commit) obj;
        }
        if (obj instanceof Blob) {
            return (Blob) obj;
        }
        return null;
    }
}
