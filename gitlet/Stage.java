package gitlet;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import static gitlet.Utils.*;
import static gitlet.Blob.*;
import static gitlet.Repository.*;


public class Stage implements Serializable {
    private Map<String, String> blobIdMap;

    public Stage() {
        this.blobIdMap = new TreeMap<>();
    }

    public void put(String fileName, String blobId) {
        if (blobId == null) {
            blobIdMap.remove(fileName);
        } else {
            blobIdMap.put(fileName, blobId);
        }
        save();
    }

    public void remove(String fileName) {
        blobIdMap.remove(fileName);
        save();
    }

    public Set<String> getFileNameSet() {
        return  blobIdMap.keySet();
    }

    public String getBlobId(String fileName) {
        return blobIdMap.get(fileName);
    }

    public Map<String, String> getBlobIdMap() {
        Map<String, String> temBlobIdMap = new HashMap<>();
        for (String fileName : blobIdMap.keySet()) {
            temBlobIdMap.put(fileName, blobIdMap.get(fileName));
        }
        return temBlobIdMap;
    }

    public boolean isEmpty() {
        return blobIdMap.isEmpty();
    }

    public boolean containFileName(String fileName) {
        return blobIdMap.containsKey(fileName);
    }

    public void update(Commit commit) {
        blobIdMap.clear();
        for (String fileName : commit.getFileNameSet()) {
            blobIdMap.put(fileName, commit.getBlobId(fileName));
        }
        save();
    }

    /**load stage, if STAGE file is empty create a stage*/
    public static Stage loadStage() {
        if (!STAGE_FILE.exists()) {
            return new Stage();
        }
        return readObject(STAGE_FILE, Stage.class);
    }

    public void save() {
        writeObject(STAGE_FILE, this);
    }

    public static Stage createStage() {
        Stage stage = new Stage();
        stage.save();
        return stage;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("=== Stage ===\n");
        for (String fileName : getFileNameSet()) {
            sb.append(fileName + " -> \n");
            Blob blob = readBlob(getBlobId(fileName));
            if (blob != null) {
                sb.append(blob);
                sb.append("\n");
            }
        }
        sb.append("\n");
        return sb.toString();
    }
}
