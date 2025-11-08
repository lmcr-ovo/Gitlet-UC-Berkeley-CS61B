package gitlet;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;

import static gitlet.MyUtils.readSerializable;
import static gitlet.Repository.*;
import static gitlet.Utils.readObject;

public class Read {
    public static void readStage() {
        System.out.println(readObject(STAGE_FILE, Stage.class));
    }

    public static void main(String[] args) {
        List<Commit> commitList = new LinkedList<>();
        List<Blob> blobList = new LinkedList<>();
        for (String id : Utils.plainFilenamesIn(OBJECT_DIR)) {
            Serializable obj = readSerializable(id);
            if (obj instanceof Commit) {
                Commit commit = (Commit) obj;
                commitList.add(commit);
            } else {
                Blob blob = (Blob) obj;
                blobList.add(blob);
            }
        }
        System.out.println("==== blob =====\n");
        for (Blob blob : blobList) {
            System.out.println(blob);
        }
        for (Commit commit : commitList) {
            System.out.println(commit);
        }
        readStage();
    }
}
