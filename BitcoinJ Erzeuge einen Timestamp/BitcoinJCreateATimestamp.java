/*
 * Herkunft/Origin: http://javacrypto.bplaced.net/
 * Programmierer/Programmer: Michael Fehr
 * Copyright/Copyright: Michael Fehr
 * Lizenttext/Licence: verschiedene Lizenzen / several licenses
 * getestet mit/tested with: Java Runtime Environment 11.0.5 x64
 * verwendete IDE/used IDE: intelliJ IDEA 2019.3.1
 * Datum/Date (dd.mm.jjjj): 21.03.2020
 * Funktion: Erzeugt den SHA256-Hashwert einer Datei und speichert ihn als Timestamp in der Bitcoin Blockchain
 * Function: create the SHA256-hash of a file a save it as timestamp in the Bitcoin blockchain
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
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

public class BitcoinJCreateATimestamp {
    private WalletAppKit kit;
    boolean kitIsRunning = false;
    private NetworkParameters netParams;
    private LocalDateTime localDateTimeStart;
    private LocalDateTime localDateTimeEnd;
    private String filenameWallet = "TimestampOwn";
    private String filenameTimestampAppend = ".timestamp.txt";
    private String filenameTimestamp;
    private Coin balance;
    private Sha256Hash sha256Hash;
    private Color colorStatus;

    public BitcoinJCreateATimestamp() throws IOException, InterruptedException {
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
                tfStatus.setText("");
                tfHash.setText("");
                tfFileLength.setText("");
                tfProofFile.setText("");
                tfFileDate.setText("");
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
                    tfFileLength.setText(Long.toString(file.length()) + " (Bytes)");
                    // perform the next steps
                    System.out.println("\nInformationen im OP_RETURN-Bereich der Transaktion: ");
                    byte[] txOpReturnByte = sha256Hash.getBytes();
                    System.out.println("txOPReturnByte: " + bytesToHex(txOpReturnByte) + " Laenge: " + txOpReturnByte.length);
                    System.out.println("\nDie Transaktion und der SendRequest werden erstellt");
                    // construct an OP_RETURN transaction
                    Transaction transaction = new Transaction(netParams);
                    transaction.addOutput(
                            Coin.ZERO,
                            ScriptBuilder.createOpReturnScript(txOpReturnByte)
                    );
                    // transaction is used to create a sendrequest
                    SendRequest sendRequest = SendRequest.forTx(transaction);
                    System.out.println("Ausgabe der Transaktion : " + transaction);
                    Coin coinsFee = Coin.valueOf(1000);
                    sendRequest.feePerKb = coinsFee; // minimum fee
                    System.out.println("Ausgabe des SendRequests: " + sendRequest);
                    System.out.println("\nSenden des SendRequests an das Wallet und das SendResult erhalten");
                    Wallet.SendResult sendResult = null;
                    try {
                        sendResult = kit.wallet().sendCoins(sendRequest);
                    } catch (InsufficientMoneyException e) {
                        System.out.println("Fehler: ungenuegendes Guthaben - laden Sie Bitcons in das Wallet");
                        tfStatus.setText("Fehler: ungenuegendes Guthaben - laden Sie Bitcons in das Wallet");
                        System.out.println("Aktuelle Empfangsadresse: " + kit.wallet().currentReceiveAddress());
                        tfStatus.setBackground(Color.RED);
                        btnFileChooser.setEnabled(false);
                        e.printStackTrace();
                    }
                    System.out.println("Ausgabe des SendResults  : " + sendResult.tx);
                    System.out.println("Transaktions Id          : " + sendResult.tx.getTxId().toString());
                    tfTxId.setText(sendResult.tx.getTxId().toString());
                    System.out.println("SHA256-Hashwert der Datei: " + tfHash.getText());
                    try {
                        tfFileDate.setText(fileLastModified(file) + " (zuletzt modifiziert)");
                        System.out.println("Dateidatum               : " + fileLastModified(file) + " (zuletzt modifiziert)");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    // save to timestamp-datei
                    tfProofFile.setText(filenameTimestamp + filenameTimestampAppend);
                    boolean filenameTimestampSaved = saveTimestamp(tfTxId.getText(), tfHash.getText(), new File(tfFile.getText()), getActualDate(), netParams.getId());
                    if (filenameTimestampSaved == true) {
                        System.out.println("Timestamp Datei erstellt: " + tfProofFile.getText());
                        tfStatus.setText("Timestamp erstellt, Datei " + tfProofFile.getText());
                        tfStatus.setBackground(Color.GREEN);
                    } else {
                        System.out.println("Timestamp Datei konnte nicht erstellt werden: " + tfProofFile.getText());
                        tfStatus.setText("Timestamp nicht erstellt, Datei " + tfProofFile.getText());
                        tfStatus.setBackground(Color.RED);
                    }
                }
                System.out.println("Das aktuelle Guthaben ist: " + kit.wallet().getBalance());
                System.out.println("Aktuelle Empfangsadresse : " + kit.wallet().currentReceiveAddress());
                tfBalance.setText(kit.wallet().getBalance().toFriendlyString() + (" = ") + kit.wallet().getBalance() + " Satoshi");
                balance = kit.wallet().getBalance();
            }
            // do nothing
        });

        btnClose.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
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
                        System.out.println("btnClose laeuft im separaten Thread");
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
                        System.out.println("Datum & Zeit am Ende : " + localDateTimeEnd.toString());
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
                System.out.println("Lade ein HD-Wallet mit BitcoinJ im Bitcoin Testnet und erzeuge eine OP_RETURN Transaktion mit reduzierten Kosten\n");
                System.out.println("Das Programm benutzt das BitcoinJ WalletAppKit\n");
                System.out.println("Bitte benutzen Sie Java 11 und bitcoinj-core-0.15.6.jar fuer dieses Programm\n");
                Thread thread = new Thread() {
                    public void run() {
                        System.out.println("btnStartKit laeuft im separaten Thread");
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

    private String bytesToHex(byte[] bytes) {
        StringBuffer result = new StringBuffer();
        for (byte b : bytes) result.append(Integer.toString((b & 0xff) + 0x100, 16).substring(1));
        return result.toString();
    }

    /* timestamp file format
    filenameTimestamp = filenameDoc + filenameTimestampAppend e.g. ".timestamp.txt"
    line 01: TxId
    line 02: Hash of filenameDoc
    line 03: filenameDoc
    line 04: length of filenameDoc
    line 05: date and time of timestamp (sendrequest)
    * method returns true if writing is successfull
    */
    private boolean saveTimestamp(String txId, String hashFilenameDoc, File fileDoc, String dateTimeTimestamp, String networkId) {
        boolean result = false;
        BufferedWriter writerSend = null;
        String filenameDoc = fileDoc.toString();
        try {
            writerSend = new BufferedWriter(new FileWriter(filenameDoc + filenameTimestampAppend));
            //writerSend.write("Informationen zum Timestamp in der Blockchain\n");
            writerSend.write(txId + "\n");
            writerSend.write(hashFilenameDoc + "\n");
            writerSend.write(filenameDoc + "\n");
            writerSend.write(Long.toString(fileDoc.length()) + "\n");
            writerSend.write(dateTimeTimestamp + "\n");
            writerSend.write("**********************************************************************************" + "\n");
            writerSend.write("******* do not edit above lines | bitte die oberen Zeilen nicht veraendern *******" + "\n");
            writerSend.write("**********************************************************************************" + "\n");
            writerSend.write("This Timestamp was created with / Dieser Zeitstempel wurde generiert mit" + "\n");
            writerSend.write("http://java-crypto.bplaced.net/bitcoinj-erzeuge-einen-timestamp/" + "\n");
            writerSend.write("Sourcecode      : " + "https://github.com/java-crypto/BitcoinJTimestamp" + "\n");
            writerSend.write("Dateiname       : " + filenameDoc + "\n");
            writerSend.write("Dateigroesse    : " + fileDoc.length() + " (Bytes)\n");
            writerSend.write("Dateidatum/Zeit : " + fileLastModified(fileDoc) + " (zuletzt modifiziert)" + "\n");
            writerSend.write("Timestamp-Datei : " + filenameDoc + filenameTimestampAppend + "\n");
            writerSend.write("Hashwert SHA256 : " + hashFilenameDoc + "\n");
            writerSend.write("Datum Timestamp : " + dateTimeTimestamp + "\n");
            writerSend.write("TransaktionsId  : " + txId + "\n");
            writerSend.write("Network-ID      : " + networkId + "\n");
            writerSend.write("**********************************************************************************" + "\n");
            writerSend.close();
            result = true;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }

    private String getActualDate() {
        // provides the actual date and time in this format dd-MM-yyyy_HH-mm-ss e.g. 16-03-2020_10-27-15
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
        LocalDateTime today = LocalDateTime.now();
        return formatter.format(today);
    }

    public String fileLastModified(File file) throws IOException {
        String fileLastModifiedDate = "";
        BasicFileAttributes bfa = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
        FileTime fileTime = bfa.lastModifiedTime();
        DateTimeFormatter DATE_FORMATTER_WITH_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss.SSS");
        fileLastModifiedDate = fileTime.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime().format(DATE_FORMATTER_WITH_TIME);
        return fileLastModifiedDate;
    }

    private static String getActualDateReverse() {
        // provides the actual date and time in this format yyyy-MM-dd_HH-mm-ss e.g. 2020-03-16_10-27-15
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
        LocalDateTime today = LocalDateTime.now();
        return formatter.format(today);
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        JFrame frame = new JFrame("Erzeuge einen Zeitstempel in der Bitcoin Blockchain");
        RedirectedFrame outputFrameErrors = new RedirectedFrame("Log Frame", true, false, true, "BitcoinJ_CreateTimestamp_Logfile_" + getActualDateReverse() + ".txt", 750, 650, JFrame.DO_NOTHING_ON_CLOSE);
        RedirectedFrame outputFrameOutput = new RedirectedFrame("Output Frame", false, true, true, "BitcoinJ_CreateTimestamp_Output_" + getActualDateReverse() + ".txt", 700, 600, JFrame.DO_NOTHING_ON_CLOSE);
        frame.setContentPane(new BitcoinJCreateATimestamp().mainPanel);
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.pack();
        frame.setSize(800, 500);
        frame.setVisible(true);
    }

    private JPanel mainPanel;
    private JButton btnFileChooser;
    private JTextField tfFile;
    private JTextField tfHash;
    private JTextField tfBalance;
    private JTextField tfFileLength;
    private JTextField tfProofFile;
    private JTextField tfTxId;
    private JTextField tfStatus;
    private JButton btnClose;
    private JButton btnStartKit;
    private JProgressBar progressBarWait;
    private JTextField tfFileDate;
}
