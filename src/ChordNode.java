import java.io.IOException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;

class Finger {
    public int key;
    public IChordNode node;
}

class Store {
    String key;
    byte[] value;
    String task;
    String taskResult;
    boolean completed;
    boolean backedUp;
}


public class ChordNode implements IChordNode, Runnable {

    static final int KEY_BITS = 8;

    //the name submitted as a key when a new node joins
    String name;

    //for each peer link, I store a reference to the successor and predecessor nodes plus a 'cached' copy of their key
    IChordNode successor;
    int successorKey;

    IChordNode predecessor;
    int predecessorKey;

    //variables for use in my finger table
    int fingerTableLength;
    Finger finger[];
    int nextFingerFix;

    //I store two vectors of stores - all tasks and backups of tasks stored on this node
    Vector<Store> dataStore = new Vector<Store>();
    Vector<Store> backUpStore = new Vector<Store>();

    //I store a boolean to check whether the node is currently stable, this is set as true when a period of time (count) has passed after a change in the successor/predecessor nodes
    static boolean stabilised = false;
    static int stableCount = 0;

    //a cached version of the key submitted when a new node joins
    private int myKey;

    /**
     * Initialises variables for a new node
     */
    public ChordNode(String myKeyString) {
        super();
        myKey = hash(myKeyString);

        successor = this;
        successorKey = myKey;
        name = myKeyString;

        //initialises the finger table
        finger = new Finger[KEY_BITS];
        for (int i = 0; i < KEY_BITS; i++)
            finger[i] = new Finger();
        fingerTableLength = KEY_BITS;

        //starts the periodic maintenance thread
        new Thread(this).start();

    }



    /**
     * Adds a new task to the data store.
     *
     * @param key       - the key submitted with the file in REST
     * @param value     - a byte array of the content of the file
     * @param task      - the task number (between t1 and t5)
     * @param result    - result of the task set when task carried out, always stored as a string
     * @param completed - set as false until task carried out
     * @param backedUp  - set as false until successfully backed up
     */
    public void put(String key, byte[] value, String task, String result, boolean completed, boolean backedUp) {
        try {
            int hashedKey = hash(key);
            //check if the key should be stored in this node, otherwise find the successor until correct node reached
            if (isInHalfOpenRangeR(hashedKey, predecessorKey, this.getKey())) {
                Store s = new Store();
                s.key = key;
                s.value = value;
                s.task = task;
                s.taskResult = result;
                s.completed = completed;
                s.backedUp = backedUp;
                this.dataStore.add(s);
                System.out.println("Added key " + hash(s.key) + " to " + this.getKey());
            } else {
                IChordNode n1 = findSuccessor(hashedKey);
                if (findSuccessor(hashedKey).getKey() == this.getKey()) {
                    if (predecessor.getKey() == findSuccessor(this.getKey()).getKey()) {
                        Store s = new Store();
                        s.key = key;
                        s.value = value;
                        s.task = task;
                        s.taskResult = result;
                        s.completed = completed;
                        s.backedUp = backedUp;
                        this.dataStore.add(s);
                    }
                    return;
                }
                n1.put(key, value, task, result, completed, backedUp);
            }
        } catch (IOException e) {
            System.out.println("Exception caught in put");
        }

    }

    /**
     * Returns the result of a task by reference to the task key
     *
     *
     */
    public String getTaskResult(String key) {
        try {
            int hashedKey = hash(key);
            //check if the key should be stored in this node, otherwise find the successor until correct node reached
            if (isInHalfOpenRangeR(hashedKey, predecessorKey, this.getKey())) {
                for (int i = 0; i < dataStore.size(); i++) {
                    System.out.println(dataStore.get(i).key);
                    if (key.equals(dataStore.get(i).key)) {
                        String result = dataStore.get(i).taskResult;
                        return result;
                    }
                }
                return null;
            } else {
                IChordNode n1 = findSuccessor(hashedKey);
                return n1.getTaskResult(key);
            }
        } catch (IOException e) {
            System.out.println("Exception caught in get task result");
        }

        return null;
    }

    /**
     * Returns the type of task by reference to the task key
     *
     *
     */
    public String getTaskID(String key) {
        try {
            int hashedKey = hash(key);
            //find the node that should hold this key, request the corresponding value from that node's local store, and return it
            if (isInHalfOpenRangeR(hashedKey, predecessorKey, this.getKey())) {
                for (int i = 0; i < dataStore.size(); i++) {
                    System.out.println(dataStore.get(i).key);
                    if (key.equals(dataStore.get(i).key)) {
                        String result = dataStore.get(i).task;
                        return result;
                    }
                }
                return null;
            } else {
                IChordNode n1 = findSuccessor(hashedKey);
                return n1.getTaskID(key);
            }
        } catch (IOException e) {
            System.out.println("Exception caught in get task ID");
        }

        return null;
    }

    /**
     * Returns a list of task keys by reference to the task results
     *
     *
     */
    public List<String> getTaskKey(List<String> result) {
        for (int j = 0; j < this.dataStore.size(); j++) {

            result.add(this.dataStore.get(j).key);
        }
        return result;
    }

    /**
     * Returns the html to display on the list of completed tasks
     *
     *
     */
    public String getTasks(String result) throws RemoteException {
        for (int j = 0; j < this.dataStore.size(); j++) {
            if (this.dataStore.get(j).completed == true) {
                String url = "http://localhost:8080/myapp/rest/get/" + this.dataStore.get(j).key;
                if (this.dataStore.get(j).task.equals("t1")) {
                    result = result + "<br>" + "<a href=\"" + url + "\">Result</a>" + " of 'number of words' - key: " + this.dataStore.get(j).key;
                }
                if (this.dataStore.get(j).task.equals("t2")) {
                    result = result + "<br>" + "<a href=\"" + url + "\">Result</a>" + " of 'average length of words' - key: " + this.dataStore.get(j).key;
                }
                if (this.dataStore.get(j).task.equals("t3")) {
                    result = result + "<br>" + "<a href=\"" + url + "\">Result</a>" + " of 'most common word' - key: " + this.dataStore.get(j).key;
                }
                if (this.dataStore.get(j).task.equals("t4")) {
                    result = result + "<br>" + "<a href=\"" + url + "\">Result</a>" + " of 'longest word' - key: " + this.dataStore.get(j).key;
                }
                if (this.dataStore.get(j).task.equals("t5")) {
                    result = result + "<br>" + "<a href=\"" + url + "\">Result</a>" + " of 'random word' - key: " + this.dataStore.get(j).key;
                }
            }
        }
        return result;
    }

    /**
     * Adds a copy of a store to the backup store of its successor
     *
     *
     */
    public void backup(String backupKey, byte[] backupValue, String backupTask, String backupTaskResult, boolean backupCompleted) {
        Store s = new Store();
        s.key = backupKey;
        s.value = backupValue;
        s.task = backupTask;
        s.taskResult = backupTaskResult;
        s.completed = backupCompleted;
        s.backedUp = false;
        this.backUpStore.add(s);
    }

    /**
     * Removes a task from a node's data store by reference to the key
     *
     *
     */
    public void removeFromData(String key) {

        for (int i = 0; i < this.dataStore.size(); i++) {
            if (this.dataStore.get(i).key.equals(key)) {
                this.dataStore.remove(i);
            }
        }
    }

    /**
     * Removes a task from a node's backup data store by reference to the key
     *
     *
     */
    public void removeFromBackupData(String key) {

        for (int i = 0; i < this.backUpStore.size(); i++) {
            if (this.backUpStore.get(i).key.equals(key)) {
                this.backUpStore.remove(i);
            }
        }
    }

    /**
     * Checks if the node is storing the backup of a store by reference to the key
     *
     *
     */
    public boolean storingBackup(String key) {
        boolean result = false;
        for (int i = 0; i < this.backUpStore.size(); i++) {
            if (this.backUpStore.get(i).key.equals(key)) {
                result = true;
            }
        }
        return result;
    }

    /**
     * Remove all backups from a node with a certain key
     *
     *
     */
    public void removeBackups(String key) {
        for (int i = 0; i < this.backUpStore.size(); i++) {
            if (this.backUpStore.get(i).key.equals(key)) {
                this.backUpStore.remove(i);
            }
        }
    }

    /**
     * returns the key of this node
     *
     *
     */
    public int getKey() {
        return myKey;
    }

    /**
     * returns the name of this node
     *
     *
     */
    public String getName() {
        return name;
    }

    /**
     * returns the predecessor of this node
     *
     *
     */
    public IChordNode getPredecessor() {
        return predecessor;
    }

    /**
     * joins the node to a given node in the network
     *
     *
     */
    public void join(IChordNode atNode) {
        try {
            predecessor = null;
            predecessorKey = 0;
            successor = atNode.findSuccessor(this.getKey());
            successorKey = successor.getKey();
        } catch (IOException e) {
            System.out.println("Exception caught in join");
        }
    }

    // -- tasks --

    /**
     * Calculates total number of words in file
     *
     *
     */
    public int totalCalculator(Store s) {
        String convertedString = new String(s.value);

        if (convertedString == null || convertedString.isEmpty()) {
            return 0;
        }

        String[] words = convertedString.split("\\s+");

        s.completed = true;
        return words.length;
    }

    /**
     * Calculates average letters in a word in the file
     *
     *
     */
    public int averageCalculator(Store s) {
        String convertedString = new String(s.value);
        int wordCount = 0;

        if (convertedString == null || convertedString.isEmpty()) {
            return 0;
        }

        String[] words = convertedString.split("\\s+");

        for (int i = 1; i < words.length; i++) {
            words[i] = words[i].replaceAll("[^a-zA-Z0-9]+", "");
        }

        int letterTotal = 0;

        for (int i = 0; i < words.length; i++) {
            letterTotal = letterTotal + words[i].length();
        }

        int wordAverage = letterTotal / words.length;
        s.completed = true;
        return wordAverage;
    }

    /**
     * Calculates most common word in the file
     *
     *
     */
    public String mostCommonWordCalculator(Store s) {
        String convertedString = new String(s.value);

        if (convertedString == null || convertedString.isEmpty()) {
            return null;
        }

        String[] words = convertedString.split("\\s+");
        for (int i = 1; i < words.length; i++) {
            words[i] = words[i].replaceAll("[^a-zA-Z0-9]+", "");
        }

        Arrays.sort(words);

        String previous = words[0];
        String popular = words[0];

        int count = 1;
        int maxCount = 1;

        for (int i = 1; i < words.length; i++) {
            if (words[i].trim().length() > 0) {
                if (words[i].equals(previous)) {
                    count++;
                } else {
                    if (count > maxCount) {
                        popular = words[i - 1];
                        maxCount = count;
                    }
                    previous = words[i];
                    count = 1;
                }
            }
        }

        s.completed = true;
        return popular;

    }

    /**
     * Calculates longest word in the file
     *
     *
     */
    public String longestWordCalculator(Store s) {
        String convertedString = new String(s.value);

        if (convertedString == null || convertedString.isEmpty()) {
            return null;
        }

        String[] words = convertedString.split("\\s+");
        for (int i = 1; i < words.length; i++) {
            words[i] = words[i].replaceAll("[^a-zA-Z0-9]+", "");
        }

        String longest = "";

        int count = 1;
        int maxCount = 1;

        for (int i = 0; i < words.length; i++) {

            if (words[i].trim().length() > longest.trim().length()) {
                longest = words[i];
            }

        }

        s.completed = true;
        return longest;
    }

    /**
     * Returns a random word in the file
     *
     *
     */
    public String randomWord(Store s) {
        String convertedString = new String(s.value);

        if (convertedString == null || convertedString.isEmpty()) {
            return null;
        }

        String[] words = convertedString.split("\\s+");
        for (int i = 1; i < words.length; i++) {
            words[i] = words[i].replaceAll("[^a-zA-Z0-9]+", "");
        }

        int index = new Random().nextInt(words.length);
        String randomWord = words[index];

        s.completed = true;
        return randomWord;
    }

    /**
     * Finds the next node with a key immediately higher than a given integer
     *
     *
     */
    public IChordNode findSuccessor(int key) {
        try {
            if (successor == this || isInHalfOpenRangeR(key, this.getKey(), successor.getKey())) {
                return successor;
            } else {
                IChordNode n = closestPrecedingNode(key);
                if (n == this || n == null) {
                    return this;
                } else {
                    return n.findSuccessor(key);
                }
            }
        } catch (IOException e) {
            System.out.println("Caught exception in find successor");
        }
        return null;

    }

    /**
     * Finds the closest preceding node of a given integer
     *
     *
     */
    public IChordNode closestPrecedingNode(int key) {

        for (int i = fingerTableLength - 1; (i >= 0 && i < fingerTableLength); i--) {
            if (isInClosedRange(finger[i].key, this.getKey(), key)) {
                return finger[i].node;
            }

        }
        return this;

    }

    // -- range check functions; they deal with the added complexity of range wraps --
    // x is in [a,b] ?
    public boolean isInOpenRange(int key, int a, int b) {
        if (b > a) return key >= a && key <= b;
        else return key >= a || key <= b;
    }

    // x is in (a,b) ?
    public boolean isInClosedRange(int key, int a, int b) {
        if (b > a) return key > a && key < b;
        else return key > a || key < b;
    }

    // x is in [a,b) ?
    public boolean isInHalfOpenRangeL(int key, int a, int b) {
        if (b > a) return key >= a && key < b;
        else return key >= a || key < b;
    }

    // x is in (a,b] ?
    public boolean isInHalfOpenRangeR(int key, int a, int b) {
        if (b > a) return key > a && key <= b;
        else return key > a || key <= b;
    }

    //this function converts a string "s" to a key that can be used with the DHT's API functions
    public int hash(String s) {
        int hash = 0;

        for (int i = 0; i < s.length(); i++)
            hash = hash * 31 + (int) s.charAt(i);

        if (hash < 0) hash = hash * -1;

        return hash % ((int) Math.pow(2, KEY_BITS));
    }

    /**
     * Takes a node and checks whether it is the predecessor
     *
     *
     */
    public void notifyNode(IChordNode potentialPredecessor) throws RemoteException {
        try {
            if (predecessor == null || isInClosedRange(potentialPredecessor.getKey(), predecessor.getKey(), this.getKey()) == true) {
                predecessor = potentialPredecessor;
                predecessorKey = potentialPredecessor.getKey();
            }
        } catch (IOException e) {
            System.out.println("caught error in notify node");
        }
    }

    /**
     *     checks to see if this node's "successor" link is still accurate
     *
     *
     */
    public void stabilise() throws RemoteException {
        try {
            if (successor != null && predecessor != null) {
                System.out.println("My key is " + this.getKey() + ", Successor is " + successor.getKey() + ", predecessor is " + predecessor.getKey());
            }
            for (int i = 0; i < this.dataStore.size(); i++) {
                System.out.println("Storing " + this.dataStore.get(i).key + " " + " - store");
            }
            for (int i = 0; i < this.backUpStore.size(); i++) {
                System.out.println("Storing " + this.backUpStore.get(i).key + " " + " - backup");
            }
            IChordNode x = successor.getPredecessor();
            if (x != null) {
                if (isInClosedRange(x.getKey(), this.getKey(), successor.getKey()) == true) {
                    successor = x;
                    successorKey = x.getKey();
                }
            } else {
                successor = deadSuccessorChecker();
                predecessor = deadPredecessorChecker();
                stabilised = false;
            }

            successor.notifyNode(this);
        } catch (IOException e) {
            System.out.println("Caught exception in stabilise");
            //check for a dead successor or predecessor if we catch an error here
            successor = deadSuccessorChecker();
            predecessor = deadPredecessorChecker();
            stabilised = false;
        }

    }

    /**
     * Checks if our successor is dead
     *
     *
     */
    public IChordNode deadSuccessorChecker() throws RemoteException {
        for (Finger i : finger) {
            try {
                if ((i.node != null) && (isInClosedRange(i.node.getKey(), myKey, predecessorKey))) {
                    //System.out.println("deadsuccessorchecker - returning node");
                    return i.node;
                }
            } catch (IOException e) {
                System.out.println("caught error in dead successor checker");
            }
        }
        return this;
    }

    /**
     * Checks if our predecessor is dead
     *
     *
     */
    public IChordNode deadPredecessorChecker() throws RemoteException {
        for (Finger i : finger) {
            try {
                if ((i.node != null) && (isInClosedRange(i.node.getKey(), successorKey, myKey))) {
                    //System.out.println("deadpredecessorchecker - returning node");
                    return i.node;
                }
            } catch (IOException e) {
                System.out.println("caught error in dead predecessor checker");
            }
        }
        return this;
    }

    /**
     * Checks on finger link each time it is called and simply performs a lookup for which node in the network should store the key attributed to that particular finger link
     *
     *
     */
    public void fixFingers() throws RemoteException {

        nextFingerFix++;
        if (nextFingerFix >= KEY_BITS) {
            nextFingerFix = 1;
        }
        IChordNode tempNode = findSuccessor(this.getKey() + ((int) Math.pow(2, nextFingerFix - 1)));
        finger[nextFingerFix].node = tempNode;
        finger[nextFingerFix].key = tempNode.getKey();

    }

    /**
     * Checks if predecessor is dead
     *
     *
     */
    public void checkPredecessor() throws RemoteException {
        try {
            this.getPredecessor();
        } catch (Exception e) {
            predecessor = deadPredecessorChecker();
        }
    }

    /**
     * Checks if a new task has been submitted that has not been completed or backed up, and completes and backs them up
     *
     *
     */
    public void checkDataStore() throws RemoteException {
        try {
            for (int i = 0; i < this.dataStore.size(); i++) {
                if (this.dataStore.get(i).completed == false) {
                    if (this.dataStore.get(i).task.equals("t1")) {
                        int result = this.totalCalculator(this.dataStore.get(i));
                        this.dataStore.get(i).taskResult = Integer.toString(result);
                    }
                    if (this.dataStore.get(i).task.equals("t2")) {
                        int result = this.averageCalculator(this.dataStore.get(i));
                        System.out.println("RESULT IS " + result);
                        this.dataStore.get(i).taskResult = Integer.toString(result);
                    }
                    if (this.dataStore.get(i).task.equals("t3")) {
                        this.dataStore.get(i).taskResult = this.mostCommonWordCalculator(this.dataStore.get(i));
                    }
                    if (this.dataStore.get(i).task.equals("t4")) {
                        this.dataStore.get(i).taskResult = this.longestWordCalculator(this.dataStore.get(i));
                    }
                    if (this.dataStore.get(i).task.equals("t5")) {
                        this.dataStore.get(i).taskResult = this.randomWord(this.dataStore.get(i));
                    }
                }
                if (this.dataStore.get(i).backedUp == false) {
                    successor.backup(this.dataStore.get(i).key, this.dataStore.get(i).value, this.dataStore.get(i).task, this.dataStore.get(i).taskResult, this.dataStore.get(i).completed);
                    this.dataStore.get(i).backedUp = true;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Checks for duplicates in the data store or backup store and removes them
     *
     *
     */
    public void checkForDuplicates() {
        if (stabilised == true) {
            List<String> keys = new ArrayList<String>();
            for (int i = 0; i < this.dataStore.size(); i++) {
                if (keys.contains(this.dataStore.get(i).key)) {
                    System.out.println("Removing " + this.dataStore.get(i).key);
                    this.dataStore.remove(i);
                }

                keys.add(this.dataStore.get(i).key);
            }
            keys.clear();
            for (int i = 0; i < this.backUpStore.size(); i++) {
                if (keys.contains(this.backUpStore.get(i).key)) {
                    System.out.println("Removing " + this.backUpStore.get(i).key);
                    this.backUpStore.remove(i);
                }

                keys.add(this.backUpStore.get(i).key);
            }
        }
    }

    /**
     * Three test cases trigger movement of stores and backup stores - if the successor of the store-holding node changes,
     * if the store-holding node leaves the network, and if the node holding the backup leaves the network.
     *
     *
     */
    public void checkBackUp() throws RemoteException {
        try {
            if (stabilised == true) {
                if (this.backUpStore.size() > 0) {
                    for (int i = 0; i < this.backUpStore.size(); i++) {
                        if (findSuccessor(hash(this.backUpStore.get(i).key)).getKey() != predecessor.getKey()) {
                            System.out.println("my key is " + this.getKey() + " whilst the key compared is " + findSuccessor(hash(this.backUpStore.get(i).key)).getKey());
                            if (findSuccessor(hash(this.backUpStore.get(i).key)).getKey() == this.getKey()) {
                                this.put(this.backUpStore.get(i).key, this.backUpStore.get(i).value, this.backUpStore.get(i).task, this.backUpStore.get(i).taskResult, this.backUpStore.get(i).completed, false);
                                System.out.println("Put " + this.backUpStore.get(i).key + " to " + this.getKey());
                                //predecessor.removeFromData(this.backUpStore.get(i).key);
                                this.backUpStore.remove(i);
                            } else {
                                predecessor.backup(this.backUpStore.get(i).key, this.backUpStore.get(i).value, this.backUpStore.get(i).task, this.backUpStore.get(i).taskResult, this.backUpStore.get(i).completed);
                                this.backUpStore.remove(i);
                            }
                        }
                    }
                }

                if (this.dataStore.size() > 0) {
                    for (int i = 0; i < this.dataStore.size(); i++) {
                        if (successor.storingBackup(this.dataStore.get(i).key) == false) {
                            successor.backup(this.dataStore.get(i).key, this.dataStore.get(i).value, this.dataStore.get(i).task, this.dataStore.get(i).taskResult, this.dataStore.get(i).completed);

                            try {
                                Registry registry = LocateRegistry.getRegistry();
                                String regList[] = registry.list();

                                for (int j = 0; j < regList.length; j++) {
                                    IChordNode stubJ = (IChordNode) registry.lookup(regList[j]);
                                    stubJ.removeBackups(this.dataStore.get(j).key);
                                }
                            } catch (Exception e) {
                                System.out.println("error caught in check back up");

                            }
                        }
                    }
                }

            }
        } catch (Exception e) {
            System.out.println("error caught in check back up");
        }
    }

    /**
     * if I'm storing data that my current predecessor should be holding, move it
     *
     *
     */
    public void checkDataMoveDown() throws RemoteException {
        try {
            for (int i = 0; i < dataStore.size(); i++) {
                if ((hash(dataStore.get(i).key) < predecessorKey) && (predecessorKey < this.getKey())) {
                    System.out.println("putting " + hash(dataStore.get(i).key) + " to " + predecessorKey);
                    predecessor.put(dataStore.get(i).key, dataStore.get(i).value, dataStore.get(i).task, this.dataStore.get(i).taskResult, this.dataStore.get(i).completed, this.dataStore.get(i).backedUp);
                    dataStore.remove(dataStore.get(i));
                }
            }
        } catch (Exception e) {
            System.out.println("error caught in check data move down");
        }
    }

    /**
     * once the node is stabilised again, set stabilised to true
     *
     *
     */
    public void changeStabilise() {
        stabilised = true;
    }

    public void run() {
        while (true) {
            stableCount++;
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                System.out.println("Interrupted");
            }

            try {
                stabilise();
            } catch (Exception e) {
                e.printStackTrace();
            }

            try {
                fixFingers();
            } catch (Exception e) {
                e.printStackTrace();
            }

            try {
                checkPredecessor();
            } catch (Exception e) {
                e.printStackTrace();
            }

            try {
                checkDataStore();
            } catch (Exception e) {
                e.printStackTrace();
            }

            try {
                checkForDuplicates();
            } catch (Exception e) {
                e.printStackTrace();
            }

            try {
                checkBackUp();
            } catch (Exception e) {
                e.printStackTrace();
            }

            try {
                checkDataMoveDown();
            } catch (Exception e) {
                e.printStackTrace();
            }


            // if our successor or predecessor change, wait 8 seconds for stabilising
            if (stabilised == false) {

                if (stableCount == 8) {
                    changeStabilise();
                }
            } else {
                stableCount = 0;
            }
        }
    }

    public static void main(String args[]) throws RemoteException {

        //get the name of the new chord node and add it to the registry, joining another node if it is not the first node
        IChordNode n1 = (IChordNode) new ChordNode(args[0]);
        Registry registry = LocateRegistry.getRegistry();
        if (registry.list().length > 0) {

            IChordNode stub = (IChordNode) UnicastRemoteObject.exportObject(n1, 0);


            int keyResult = 0;
            while (keyResult == 0) {


                Random generator = new Random();
                int stubNum = generator.nextInt(registry.list().length);
                String regList[] = registry.list();


                try {
                    IChordNode stubI = (IChordNode) registry.lookup(regList[stubNum]);
                    keyResult = stubI.getKey();
                    registry.rebind(args[0], stub);
                    stub.join(stubI);
                    System.out.println(n1.getName() + " has joined " + stubI.getName());
                    System.out.println("Registry size is now " + registry.list().length);
                } catch (Exception e) {
                    try {
                        registry.unbind(regList[stubNum]);
                    } catch (NotBoundException nbe) {
                        System.out.println("Not bound exception in main method");
                    }
                }


            }
        } else {

            IChordNode stub = (IChordNode) UnicastRemoteObject.exportObject(n1, 0);
            registry.rebind(args[0], stub);
            System.out.println(n1.getKey() + " has joined");
            System.out.println("Registry size is now " + registry.list().length);
        }

        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            System.out.println("Interrupted");
        }

        stabilised = true;
        System.out.println("Stabilised");

    }

}
