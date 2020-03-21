/*
 * Herkunft/Origin: http://javacrypto.bplaced.net/
 * Programmierer/Programmer: Michael Fehr
 * Copyright/Copyright: Michael Fehr
 * Lizenttext/Licence: verschiedene Lizenzen / several licenses
 * getestet mit/tested with: Java Runtime Environment 11.0.5 x64
 * verwendete IDE/used IDE: intelliJ IDEA 2019.3.1
 * Datum/Date (dd.mm.jjjj): 21.03.2020
 * Funktion: Ueberprueft die Timestamp-Datei in der Bitcoin Blockchain
 * Function: verifies the timestamp-file in Bitcoin blockchain
 *
 * Sicherheitshinweis/Security notice
 * Die Programmroutinen dienen nur der Darstellung und haben keinen Anspruch auf eine korrekte Funktion,
 * insbesondere mit Blick auf die Sicherheit !
 * Prüfen Sie die Sicherheit bevor das Programm in der echten Welt eingesetzt wird.
 * The program routines just show the function but please be aware of the security part -
 * check yourself before using in the real world !
 *
 * Sie benötigen diverse Bibliotheken (alle im Github-Archiv im Unterordner "libs")
 * You need a lot of libraries (see my Github-repository in subfolder "libs")
 * verwendete BitcoinJ-Bibliothek / used BitcoinJ Library: bitcoinj-core-0.15.6.jar
 * my Github-Repository: https://github.com/java-crypto/BitcoinJ
 * libs in my Github-Repo: https://github.com/java-crypto/BitcoinJ_Libraries
 *
 */

import org.bitcoinj.core.*;
import org.bitcoinj.net.discovery.DnsDiscovery;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptChunk;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.MemoryBlockStore;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class BitcoinJVerifyATimestamp {

    PeerGroup peerGroup;
    public NetworkParameters netParams;
    private LocalDateTime localDateTimeStart;
    private LocalDateTime localDateTimeEnd;
    private String filenameTimestamp;
    private String filenameTimestampExtendedAppend = ".timestamp_extended.txt";
    private int searchTimeout = 20; // seconds for searching
    private Sha256Hash sha256Hash;
    private Color colorStatus;

    public BitcoinJVerifyATimestamp() throws IOException, InterruptedException {
        // choose network type (MAIN, TEST or REG)
        //String networkType = "MAIN";
        String networkType = "TEST";
        //String networkType = "REG";
        switch (networkType) {
            case "MAIN": {
                netParams = MainNetParams.get();
                break;
            }
            case "TEST": {
                netParams = TestNet3Params.get();
                break;
            }
            case "REG": {
                netParams = RegTestParams.get();
                break;
            }
            default: {
                System.out.println("Es ist kein networkType angegeben, das Programm wird in 10 Sekunden beendet");
                tfStatus.setText("Kein networkType angegeben, Programm endet in 10 Sekunden");
                tfStatus.setBackground(Color.RED);
                TimeUnit.SECONDS.sleep(10);
                System.exit(0);
            }
        }
        System.out.println("Das Programm arbeitet im Netzwerk: " + netParams.getId());
        System.out.println("Guten Tag, zum Start bitte den Button 'waehlen Sie die Datei um den Zeitstempel zu verifizieren' druecken");
        localDateTimeStart = LocalDateTime.now();
        colorStatus = tfStatus.getBackground();
        progressBarWait.setVisible(false);
        progressBarWait.setIndeterminate(false);

        btnFileChooser.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                tfFile.setText("");
                tfHash.setText("");
                tfBlockHeight.setText("");
                tfBlockHash.setText("");
                tfProofFile.setText("");
                tfTxId.setText("");
                tfStatus.setText("");
                filenameTimestamp = "";
                sha256Hash = null;
                tfStatus.setBackground(colorStatus);
                File file = chooseFile();
                try {
                    tfFile.setText(file.toString());
                } catch (NullPointerException e) {
                }
                if (tfFile.getText() != "") {
                    filenameTimestamp = tfFile.getText();
                    System.out.println("Datei dessen Timestamp ueberprueft werden soll: " + filenameTimestamp);
                    try {
                        sha256Hash = Sha256Hash.of(file);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    tfHash.setText(String.valueOf(sha256Hash));
                    // perform the next steps
                    System.out.println("\nInformationen im OP_RETURN-Bereich der Transaktion werden verifiziert:");
                    // wir prüfen ob die proof-datei existiert
                    String filenameExtendedTimestamp = tfFile.getText() + filenameTimestampExtendedAppend;
                    tfProofFile.setText(filenameExtendedTimestamp);
                    File fileTimestamp = new File(filenameExtendedTimestamp);
                    System.out.println("Die erweiterte Timestampdatei " + filenameExtendedTimestamp + " ist vorhanden: " + fileTimestamp.exists());
                    if (!fileTimestamp.exists()) {
                        System.out.println("Die erweiterte Timestampdatei ist nicht vorhanden und kann nicht verifiziert werden");
                        return;
                    }
                    if (!fileTimestamp.canRead() || !fileTimestamp.isFile()) {
                        //System.exit(0);
                        return;
                    }
                    tfStatus.setText("warten auf die Antwort eines Nodes (maximal " + searchTimeout + " Sekunden)");
                    tfStatus.setBackground(Color.YELLOW);
                    BufferedReader in = null;
                    String zeile = "";
                    try {
                        in = new BufferedReader(new FileReader(filenameExtendedTimestamp));
                        zeile = null;
                        // nur drei zeilen werden gelesen
                        // erste zeile transaction hash
                        tfTxId.setText(in.readLine().toUpperCase());
                        // zweite zeile block hash
                        tfBlockHash.setText(in.readLine());
                        // dritte zeile block height
                        tfBlockHeight.setText(in.readLine());
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        if (in != null)
                            try {
                                in.close();
                            } catch (IOException e) {
                            }
                    }
                    try {
                        in.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    System.out.println(("TxId in der Datei: " + tfTxId.getText()));
                    Sha256Hash blockHash = Sha256Hash.wrap(tfBlockHash.getText());
                    byte[] hashToFind = hexStringToByteArray(tfHash.getText());

                    btnFileChooser.setEnabled(false);
                    progressBarWait.setString("Der Node wird kontaktiert, bitte warten ...");
                    progressBarWait.setStringPainted(true);
                    progressBarWait.setVisible(true);
                    progressBarWait.setIndeterminate(true);
                    Thread thread = new Thread() {
                        public void run() {
                            System.out.println("btn chooseFile Thread Running");
                            // ab hier wird verifiziert
                            BlockStore blockStore = new MemoryBlockStore(netParams);
                            BlockChain chain = null;
                            try {
                                chain = new BlockChain(netParams, blockStore);
                            } catch (BlockStoreException e) {
                                e.printStackTrace();
                            }
                            peerGroup = new PeerGroup(netParams, chain);
                            peerGroup.setUserAgent("Sample App", "1.0");
                            // ausführung nicht im REG / RegressionNetwork
                            if (netParams != RegTestParams.get()) {
                                peerGroup.addPeerDiscovery(new DnsDiscovery(netParams));
                            }
                            PeerAddress addr = null;
                            try {
                                addr = new PeerAddress(netParams, InetAddress.getLocalHost());
                            } catch (UnknownHostException e) {
                                e.printStackTrace();
                            }
                            peerGroup.addAddress(addr);
                            peerGroup.start();
                            try {
                                peerGroup.waitForPeers(1).get();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            } catch (ExecutionException e) {
                                e.printStackTrace();
                            }
                            Peer peer = peerGroup.getConnectedPeers().get(0);
                            // System.out.println("Vor: Future<Block> future = peer.getBlock(blockHash);");
                            Future<Block> future = peer.getBlock(blockHash);
                            System.out.println("Warten auf die Antwort eines Nodes mit dem gesuchten Block: " + blockHash);
                            Block block = null;
                            try {
                                //block = future.get();
                                block = future.get(searchTimeout, TimeUnit.SECONDS);
                            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                                //} catch (InterruptedException | ExecutionException e) {
                                e.printStackTrace();
                                System.out.println("Der Node hat kein Ergebnis innerhalb von " + searchTimeout + " Sekunden abgeliefert, die Suche endet jetzt");
                                tfStatus.setText("Suche ergebnislos beendet (Timeout von " + searchTimeout + " Sekunden)");
                                tfStatus.setBackground(Color.ORANGE);
                                btnFileChooser.setEnabled(true);
                                progressBarWait.setVisible(false);
                                progressBarWait.setIndeterminate(false);
                                future.cancel(true); //this method will stop the running underlying task
                                return;
                            }
                            //System.out.println("Hier das Ergebnis des Node Blocks:\n" + block);
                            System.out.println("Gesuchte Transaktion: " + tfTxId.getText());
                            System.out.println("Gesuchter Hash      : " + tfHash.getText());
                            boolean txOpReturnFound = findTx(block, tfTxId.getText(), tfHash.getText());
                            if (txOpReturnFound == true) {
                                System.out.println("Timestamp BESTAETIGT - der Hash wurde in der Transaktion gefunden");
                                tfStatus.setText("Timestamp BESTAETIGT - der Hash wurde in der Transaktion gefunden");
                                tfStatus.setBackground(Color.GREEN);
                            } else {
                                tfStatus.setText("Timestamp NICHT BESTAEIGT - der Hash wurde NICHT in der Transaktion gefunden");
                                System.out.println("Timestamp NICHT BESTAETIGT - der Hash wurde NICHT in der Transaktion gefunden");
                                tfStatus.setBackground(Color.RED);
                            }
                            progressBarWait.setString("Verbindung zum Node wird beendet, bitte warten ...");
                            peerGroup.stopAsync();
                            btnFileChooser.setEnabled(true);
                            progressBarWait.setVisible(false);
                            progressBarWait.setIndeterminate(false);
                        }
                    };
                    thread.start();

                }
                // do nothing
            }
        });
        btnClose.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                localDateTimeEnd = LocalDateTime.now();
                tfStatus.setText("Das Programm wird beendet, bitte warten ...");
                System.out.println("Date & Time at Start: " + localDateTimeStart.toString());
                System.out.println("Date & Time at End:   " + localDateTimeEnd.toString());
                System.exit(0);
            }
        });
    }

    public boolean findTx(Block block, String tfTxId, String tfHash) {
        boolean txFound = false;
        int txIdFound = 0;
        boolean txOpReturnFound = false;
        // get transactions
        List<Transaction> transactions = block.getTransactions();
        int transactionsSize = transactions.size();
        //System.out.println("There are " + transactionsSize + " transactions in block");
        for (int i = 0; i < transactionsSize; i++) {
            Transaction tx = transactions.get(i);
            String txId = tx.getTxId().toString().toUpperCase();
            //System.out.println("txId: " + txId);
            //System.out.println("txId " + i + " ist " + txId);
            if (txId.equals(tfTxId)) {
                //System.out.println(" * Transaktion " + tfTxId + " gefunden in Nr " + i);
                txFound = true;
                txIdFound = i;
                break;
            }
        }
        if (txFound == true) {
            Transaction txWorks = transactions.get(txIdFound);
            List<TransactionOutput> txOutputs = txWorks.getOutputs();
            int txOutputsSize = txOutputs.size();
            //System.out.println("   There are " + txOutputsSize + " Outputs in the transaction");
            for (int i = 0; i < txOutputsSize; i++) {
                TransactionOutput txOutput = txOutputs.get(i);
                //System.out.println("   Output Nr " + i + " ist " + txOutput.toString());
                Script txOutputScriptPubKey = txOutput.getScriptPubKey();
                //System.out.println("Script PubKey: " + txOutputScriptPubKey);
                Script.ScriptType txOutputScryptType = txOutputScriptPubKey.getScriptType();
                //System.out.println("ScriptType: " + txOutputScryptType);
                List<ScriptChunk> txOutputScriptPubKeyChunks = txOutputScriptPubKey.getChunks();
                int txOutputScriptPubKeyChunksSize = txOutputScriptPubKeyChunks.size();
                //System.out.println("Chunks: " + txOutputScriptPubKeyChunksSize);
                for (int j = 0; j < txOutputScriptPubKeyChunksSize; j++) {
                    ScriptChunk chunk = txOutputScriptPubKeyChunks.get(j);
                    //System.out.println("Chunk " + j + " ist " + chunk);
                    //System.out.println("chunk opcode " + chunk.opcode);
                    byte[] chunkData = null;
                    //System.out.println("Suche nun chunkData");
                    try {
                        chunkData = chunk.data;
                        //System.out.println("chunkData : " + bytesToHex(chunkData));
                        byte[] hashToFind = hexStringToByteArray(tfHash);
                        //System.out.println("hashToFind: " + bytesToHex(hashToFind));
                        if (Arrays.equals(chunkData, hashToFind)) {
                            //System.out.println("    *** Hash gefunden***");
                            txOpReturnFound = true;
                        }
                    } catch (NullPointerException e) {
                        //System.out.println("NPE keine chunkData gefunden");
                    }
                }
            }
        }
        return txOpReturnFound;
    }

    private File chooseFile() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            return chooser.getSelectedFile();
        } else {
            return null;
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuffer result = new StringBuffer();
        for (byte b : bytes) result.append(Integer.toString((b & 0xff) + 0x100, 16).substring(1));
        return result.toString();
    }

    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    private static String getActualDateReverse() {
        // provides the actual date and time in this format yyyy-MM-dd_HH-mm-ss e.g. 2020-03-16_10-27-15
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
        LocalDateTime today = LocalDateTime.now();
        return formatter.format(today);
    }

    public static void main(String[] args) throws IOException, InterruptedException {

        JFrame frame = new JFrame("Ueberpruefe einen Timestamp");
        // umleitung der konsole in 2 fenster
        RedirectedFrame outputFrameErrors = new RedirectedFrame("Log Frame", true, false, true, "BitcoinJ_VerifyTimestamp_Logfile_" + getActualDateReverse() + ".txt", 750, 650, JFrame.DO_NOTHING_ON_CLOSE);
        RedirectedFrame outputFrameOutput = new RedirectedFrame("Output Frame", false, true, true, "BitcoinJ_VerifyTimestamp_Output_" + getActualDateReverse() + ".txt", 700, 600, JFrame.DO_NOTHING_ON_CLOSE);
        frame.setContentPane(new BitcoinJVerifyATimestamp().mainPanel);
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.pack();
        frame.setSize(700, 450);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                System.exit(0);
            }
        });
        frame.setVisible(true);
    }

    private JPanel mainPanel;
    private JButton btnFileChooser;
    private JTextField tfFile;
    private JTextField tfHash;
    private JTextField tfBlockHeight;
    private JTextField tfProofFile;
    private JTextField tfTxId;
    private JTextField tfStatus;
    private JButton btnClose;
    private JTextField tfBlockHash;
    private JProgressBar progressBarWait;
}
