import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Vector;
import java.io.*;
import java.util.regex.*;
import javax.imageio.*;


public interface IChordNode extends Remote {

    void put(String key, byte[] value, String task, String result, boolean completed, boolean backedUp) throws RemoteException;

    String getTaskResult(String key) throws RemoteException;

    String getTaskID(String key) throws RemoteException;

    List<String> getTaskKey(List<String> key) throws RemoteException;

    String getTasks(String result) throws RemoteException;

    void backup(String backupKey, byte[] backupValue, String backupTask, String backupTaskResult, boolean backupCompleted) throws RemoteException;

    void removeFromBackupData(String key) throws RemoteException;

    void removeFromData(String key) throws RemoteException;

    boolean storingBackup(String key) throws RemoteException;

    void removeBackups(String key) throws RemoteException;

    int getKey() throws RemoteException;

    String getName() throws RemoteException;

    IChordNode getPredecessor() throws RemoteException;

    void join(IChordNode atNode) throws RemoteException;

    int totalCalculator(Store s) throws RemoteException;

    int averageCalculator(Store s) throws RemoteException;

    String mostCommonWordCalculator(Store s) throws RemoteException;

    String longestWordCalculator(Store s) throws RemoteException;

    IChordNode findSuccessor(int key) throws RemoteException;

    IChordNode closestPrecedingNode(int key) throws RemoteException;

    int hash(String s) throws RemoteException;

    void notifyNode(IChordNode potentialPredecessor) throws RemoteException;

    void stabilise() throws RemoteException;

    IChordNode deadSuccessorChecker() throws RemoteException;

    IChordNode deadPredecessorChecker() throws RemoteException;

    void fixFingers() throws RemoteException;

    void checkPredecessor() throws RemoteException;

    void checkDataMoveDown() throws RemoteException;

    public void run() throws RemoteException;

}
