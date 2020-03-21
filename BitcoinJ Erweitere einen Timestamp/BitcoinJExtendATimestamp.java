/*
 * Herkunft/Origin: http://javacrypto.bplaced.net/
 * Programmierer/Programmer: Michael Fehr
 * Copyright/Copyright: Michael Fehr
 * Lizenttext/Licence: verschiedene Lizenzen / several licenses
 * getestet mit/tested with: Java Runtime Environment 11.0.5 x64
 * verwendete IDE/used IDE: intelliJ IDEA 2019.3.1
 * Datum/Date (dd.mm.jjjj): 21.03.2020
 * Funktion: Erweitert die Timestamp-Datei um den Block-Hash und die Block Height
 * Function: appends the timestamp-file with block-hash and block-height
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
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class BitcoinJExtendATimestamp {

    private WalletAppKit kit;
    boolean kitIsRunning = false;
    private NetworkParameters netParams;
    private LocalDateTime localDateTimeStart;
    private LocalDateTime localDateTimeEnd;
    private String filenameWallet = "TimestampOwn";
    private String filenameTimestampAppend = ".timestamp.txt";
    private String filenameTimestampExtendedAppend = ".timestamp_extended.txt";
    private String filenameTimestamp;
    private Coin balance;
    private Sha256Hash sha256Hash;
    private Color colorStatus;

    public BitcoinJExtendATimestamp() throws IOException, InterruptedException {
        // choose network type (MAIN, TEST or REG)
        //String networkType = "MAIN";
        String networkType = "TEST";
        //String networkType = "REG";
        switch (networkType) {
            case "MAIN": {
                netParams = MainNetParams.get();
                filenameWallet = filenameWallet + "_Mainnet";
                break;
            }
            case "TEST": {
                netParams = TestNet3Params.get();
                filenameWallet = filenameWallet + "_Testnet";
                break;
            }
            case "REG": {
                netParams = RegTestParams.get();
                filenameWallet = filenameWallet + "_Regtest";
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
        System.out.println("Guten Tag, zum Start bitte den Button 'starte das wallet' druecken");
        localDateTimeStart = LocalDateTime.now();
        kitIsRunning = false;
        colorStatus = tfStatus.getBackground();

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
                tfStatus.setBackground(colorStatus);
                filenameTimestamp = "";
                sha256Hash = null;
                File file = chooseFile();
                try {
                    tfFile.setText(file.toString());
                } catch (NullPointerException e) {
                }
                if (tfFile.getText() != "") {
                    filenameTimestamp = tfFile.getText();
                    System.out.println("Datei die mit einem Timestamp versehen werden soll: " + filenameTimestamp);
                    try {
                        sha256Hash = Sha256Hash.of(file);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    tfHash.setText(String.valueOf(sha256Hash));
                    // perform the next steps
                    System.out.println("\nInformationen im OP_RETURN-Bereich der Transaktion werden gesucht:");

                    // wir prüfen ob die proof-datei existiert
                    String filenameTimestamp = tfFile.getText() + filenameTimestampAppend;
                    tfProofFile.setText(filenameTimestamp);
                    File fileTimestamp = new File(filenameTimestamp);
                    System.out.println("Die Timestamp-Datei " + filenameTimestamp + " ist vorhanden: " + fileTimestamp.exists());
                    if (!fileTimestamp.exists()) {
                        System.out.println("Die Timestamp-Datei ist nicht vorhanden und kann nicht erweitert werden");
                        return;
                    }
                    if (!fileTimestamp.canRead() || !fileTimestamp.isFile()) {
                        return;
                    }
                    BufferedReader in = null;
                    String zeile = "";
                    try {
                        in = new BufferedReader(new FileReader(filenameTimestamp));
                        zeile = null;
                        // nur die erste zeile wird gelesen
                        zeile = in.readLine();
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
                    System.out.println(("TxId in der Datei: " + zeile));
                    String TxId = zeile;
                    tfTxId.setText(TxId);
                    // einlesen aller eigenen tx aus dem wallet und sortierung
                    ArrayList<Transaction> txList = new ArrayList(kit.wallet().getTransactions(true));
                    txList.sort((tx1, tx2) -> {
                        return tx2.getUpdateTime().compareTo(tx1.getUpdateTime());
                    });

                    boolean txFund = false;
                    int block = 0;
                    int blockDepth = 0;
                    for (int i = 0; i < txList.size(); i++) {
                        Transaction tx = txList.get(i);
                        String TxTxId = tx.getTxId().toString();
                        if (TxTxId.equals(TxId)) {
                            // System.out.println("* * * Fund in Tx Nr. " + i);
                            txFund = true;
                            try {
                                block = tx.getConfidence().getAppearedAtChainHeight();
                            } catch (IllegalStateException e) {
                                System.out.println("Exception block " + e);
                                System.out.println("Die Transaktion wurde gefunden, ist aber noch nicht in einem Block vorhanden");
                                tfStatus.setText("Die Transaktion wurde noch nicht in einem Block gefunden");
                                tfStatus.setBackground(Color.YELLOW);
                                txFund = false;
                                break;
                            }
                            tfBlockHeight.setText(String.valueOf(block));
                            try {
                                blockDepth = tx.getConfidence().getDepthInBlocks();
                            } catch (Exception e) {
                                //System.out.println("Exception depth " + e);
                            }
                            System.out.println("Die Transaktion wurde gefunden und die erweiterte Timestamp-Datei wurde erzeugt");
                            tfStatus.setText("Transaktion gefunden und die erweiterte Timestamp-Datei erzeugt");
                            tfStatus.setBackground(Color.GREEN);
                            break; // schleife beenden
                        } else {
                            txFund = false;
                            System.out.println("Die Transaktion wurde nicht gefunden");
                            tfStatus.setText("Die Transaktion wurde nicht gefunden");
                        }
                    }
                    if (txFund == true) {
                        int heightToFind = Integer.parseInt(tfBlockHeight.getText());
                        // wir benötigen den blockhash für eine spätere direkte suche
                        // System.out.println("\n*** Suche den Blockhash ***");
                        BlockChain chain = kit.chain();
                        BlockStore bs = chain.getBlockStore();
                        Peer peer = kit.peerGroup().getDownloadPeer();
                        // Get last block
                        StoredBlock current = null;
                        try {
                            current = bs.getChainHead();
                        } catch (BlockStoreException e) {
                            e.printStackTrace();
                        }
                        //System.out.println("*** current = last block:\n" + current.toString() + "\n");
                        //System.out.println("*** Last Block (height) getHeight: " + current.getHeight() + "\n");
                        //System.out.println("Wir arbeiten im folgenden Netz:" + kit.params().getId());
                        int nr = 1;
                        // Loop until you reach the genesis block
                        int heightStart = current.getHeight();
                        String heightToFindHash = "";
                        //System.out.println("* * * heightStart: " + heightStart);
                        while (current.getHeight() > 1) {
                            //System.out.println("** INT: " + nr);
                            if ((1) <= current.getHeight() && current.getHeight() <= heightStart) { // 28.02.2020 22:50
                                //System.out.println("* current height:\n" + current.getHeight());
                                Block b = null;
                                try {
                                    b = peer.getBlock(current.getHeader().getHash()).get();
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                } catch (ExecutionException e) {
                                    e.printStackTrace();
                                }
                                //System.out.println("* Block:\n" + b);
                                int blockHeight = current.getHeight();
                                if (blockHeight == heightToFind) {
                                    //System.out.println("Der Block " + blockHeight + " wurde gefunden");
                                    heightToFindHash = current.getHeader().getHashAsString();
                                    tfBlockHash.setText(heightToFindHash);
                                    //System.out.println("Der Block " + blockHeight + " hat diesen Hashwert:\n" + heightToFindHash);
                                    break;
                                }
                            }
                            try {
                                current = current.getPrev(bs);
                            } catch (BlockStoreException e) {
                                e.printStackTrace();
                            }
                            //System.out.println("** current nach getPrev(bs):\n" + current.toString());
                            nr++;
                            //if (nr == 5) System.exit(0);
                        }

                        // schreiben der erweiterte timestamp-datei
                        String filenameExtendedTimestamp = filenameTimestamp.replace(filenameTimestampAppend, filenameTimestampExtendedAppend);
                        System.out.println("Speicherung des erweiterten Timestamp in die Datei:\n" + filenameExtendedTimestamp);
                        // extended proof schreiben
                        BufferedWriter writer = null;
                        try {
                            writer = new BufferedWriter(new FileWriter(filenameExtendedTimestamp));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        // timestamp lesen und in erweiterte timstamp-datei schreiben
                        BufferedReader in2 = null;
                        int lineRead = 0; // read and write first line = txid
                        // write lines 2 + 3 for blockhash and blockheigth
                        // read and write other lines
                        in2 = null;
                        String readZeile = "";
                        try {
                            in2 = new BufferedReader(new FileReader(fileTimestamp));
                            readZeile = null;
                            while ((readZeile = in2.readLine()) != null) {
                                lineRead++;
                                if (lineRead == 2) {
                                    try {
                                        writer.write(tfBlockHash.getText() + "\n");
                                        writer.write(tfBlockHeight.getText() + "\n");
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                }
                                //System.out.println("Gelesene Zeile: " + readZeile);
                                try {
                                    writer.write(readZeile + "\n");
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        } finally {
                            if (in2 != null)

                                try {
                                    in2.close();
                                } catch (IOException e) {
                                }
                        }
                        try {
                            writer.write("Block Hash      : " + tfBlockHash.getText() + "\n");
                            writer.write("Block Height    : " + tfBlockHeight.getText() + "\n");
                            writer.write("**********************************************************************************" + "\n");
                            writer.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
                // do nothing
            }
        });
        btnClose.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                //shutDownApplication();
                btnFileChooser.setEnabled(false);
                btnClose.setEnabled(false);
                btnStartKit.setEnabled(false);
                progressBarWait.setString("Das Programm wird beendet, bitte warten ...");
                tfStatus.setText("Das Programm wird beendet, bitte warten ...");
                progressBarWait.setStringPainted(true);
                progressBarWait.setVisible(true);
                progressBarWait.setIndeterminate(true);
                Thread thread = new Thread() {
                    public void run() {
                        System.out.println("btnClose Thread Running");
                        // stop kit
                        try {
                            Thread.sleep(1000); // 5 seconds to end
                        } catch (InterruptedException e) {
                        }
                        if (kitIsRunning) {
                            kit.stopAsync();
                            kit.awaitTerminated();
                        }
                        localDateTimeEnd = LocalDateTime.now();
                        System.out.println("Das Programm endet jetzt");
                        System.out.println("Datum & Zeit am Start: " + localDateTimeStart.toString());
                        System.out.println("Datum & Zeit am Ende :  " + localDateTimeEnd.toString());
                        System.exit(0);
                    }
                };
                thread.start();
            }
        });

        btnStartKit.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                // disable start button to prevent a second start
                btnStartKit.setEnabled(false);
                progressBarWait.setString("Das Wallet wird aktualisiert, bitte warten ...");
                progressBarWait.setStringPainted(true);
                progressBarWait.setVisible(true);
                progressBarWait.setIndeterminate(true);
                if (kitIsRunning) {
                    System.out.println("Das Wallet ist bereits gestartet");
                    return;
                }
                // actual date and time
                localDateTimeStart = LocalDateTime.now();
                System.out.println("Lade ein HD-Wallet mit BitcoinJ im Bitcoin Testnet und erweitere einen Timestamp um die Block-Hash\n");
                System.out.println("Das Programm benutzt das BitcoinJ WalletAppKit\n");
                System.out.println("Bitte benutzen Sie Java 11 und bitcoinj-core-0.15.6.jar fuer dieses Programm\n");
                Thread thread = new Thread() {
                    public void run() {
                        System.out.println("btn startKit Thread Running");
                        kit = new WalletAppKit(netParams, new File("."), filenameWallet);
                        kit.setAutoSave(true);
                        if (netParams == RegTestParams.get()) {
                            kit.connectToLocalHost(); // für regtest notwendig
                        }
                        System.out.println("Das Wallet wurde geladen: " + filenameWallet);
                        System.out.println("\nDas Wallet aktualisiert die Blockchain in der Datei " + filenameWallet + ".spvchain");
                        System.out.println("Bitte haben Sie eine bis drei Minuten Geduld");
                        kit.startAsync();
                        kit.awaitRunning();
                        while (!kit.isRunning()) {
                            try {
                                wait(1000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                        // kit is running
                        progressBarWait.setVisible(false);
                        progressBarWait.setIndeterminate(false);
                        kit.wallet().allowSpendingUnconfirmedTransactions(); // nur eigene gelder, daher erlaube ich das mal
                        System.out.println("Das aktuell verfuegbare Guthaben ist: " + kit.wallet().getBalance().toFriendlyString() + (" = ") + kit.wallet().getBalance() + " Satoshi");
                        tfBalance.setText(kit.wallet().getBalance().toFriendlyString() + (" = ") + kit.wallet().getBalance() + " Satoshi");
                        balance = kit.wallet().getBalance();
                        System.out.println("Aktuelle Empfangsadresse            : " + kit.wallet().currentReceiveAddress());
                        System.out.println("Das Programm ist betriebsbereit und wartet auf den Einsatz");
                        kitIsRunning = true;
                        tfStatus.setText("Das WalletAppKit laeuft");
                        btnFileChooser.setEnabled(true);
                    }
                };
                thread.start();
            }
        });
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

    private static String getActualDateReverse() {
        // provides the actual date and time in this format yyyy-MM-dd_HH-mm-ss e.g. 2020-03-16_10-27-15
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
        LocalDateTime today = LocalDateTime.now();
        return formatter.format(today);
    }

    public static void main(String[] args) throws IOException, InterruptedException {

        JFrame frame = new JFrame("Erweitere einen Timestamp fuer die Bitcoin Blockchain");
        RedirectedFrame outputFrameErrors = new RedirectedFrame("Log Frame", true, false, true, "BitcoinJ_ExtendTimestamp_Logfile_" + getActualDateReverse() + ".txt", 750, 650, JFrame.DO_NOTHING_ON_CLOSE);
        RedirectedFrame outputFrameOutput = new RedirectedFrame("Output Frame", false, true, true, "BitcoinJ_ExtendTimestamp_Output_" + getActualDateReverse() + ".txt", 700, 600, JFrame.DO_NOTHING_ON_CLOSE);
        frame.setContentPane(new BitcoinJExtendATimestamp().mainPanel);
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.pack();
        frame.setSize(600, 450);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                //System.exit(0);
                // shutdownApplication();
                System.out.println("Bitte drücken Sie den 'Ende des Programms' Button");
            }
        });
        frame.setVisible(true);
    }

    private JPanel mainPanel;
    private JButton btnFileChooser;
    private JTextField tfFile;
    private JTextField tfHash;
    private JTextField tfBalance;
    private JTextField tfBlockHeight;
    private JTextField tfProofFile;
    private JTextField tfTxId;
    private JTextField tfStatus;
    private JButton btnClose;
    private JButton btnStartKit;
    private JTextField tfBlockHash;
    private JProgressBar progressBarWait;
}
