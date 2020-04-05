package timestamper;

// ### import com.subgraph.orchid.*;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.event.ActionEvent;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.util.Callback;
import javafx.util.Duration;
import org.bitcoinj.core.*;
import org.bitcoinj.core.listeners.BlocksDownloadedEventListener;
import org.bitcoinj.core.listeners.DownloadProgressTracker;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptChunk;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.MemoryBlockStore;
import org.bitcoinj.utils.MonetaryFormat;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;
import org.fxmisc.easybind.EasyBind;
import timestamper.controls.ClickableBitcoinAddress;
import timestamper.controls.NotificationBarPane;
import timestamper.utils.BitcoinUIModel;
import timestamper.utils.GuiUtils;
import timestamper.utils.easing.EasingMode;
import timestamper.utils.easing.ElasticInterpolator;

import javax.annotation.Nullable;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static timestamper.Main.bitcoin;
import static timestamper.Main.params;
import static timestamper.utils.GuiUtils.crashAlert;
import static timestamper.utils.GuiUtils.informationalAlert;

/**
 * Gets created auto-magically by FXMLLoader via reflection. The widget fields are set to the GUI controls they're named
 * after. This class handles all the updates and event handling for the main UI.
 */
public class MainController {
    public HBox controlsBox;
    public Label balance;
    public Button sendMoneyOutBtn;
    public ClickableBitcoinAddress addressControl;
    public ListView<Proof> pendingProofsList;
    private String filenameTimestampAppend = ".timestamp.txt";

    private static class Proof implements Serializable {
        byte[] tx, partialMerkleTree;
        Sha256Hash blockHash;
        // ### new in version 2 to prevent a saving without partialMerkleTree
        boolean partialMerkleTreeFilled;
        boolean proofDepthMinimum;
        transient SimpleIntegerProperty depth = new SimpleIntegerProperty();
        transient String filename;

        public void saveTo(String filename) throws IOException {
            try (ObjectOutputStream oos = new ObjectOutputStream(Files.newOutputStream(Paths.get(filename)))) {
                oos.writeObject(this);
            }
        }

        public static Proof readFrom(String filename) throws IOException {
            try (ObjectInputStream ois = new ObjectInputStream(Files.newInputStream(Paths.get(filename)))) {
                return (Proof) ois.readObject();
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private BitcoinUIModel model = new BitcoinUIModel();
    private NotificationBarPane.Item syncItem;

    // Called by FXMLLoader.
    public void initialize() {
        addressControl.setOpacity(0.0);

        pendingProofsList.setCellFactory(new Callback<ListView<Proof>, ListCell<Proof>>() {
            @Override
            public ListCell<Proof> call(ListView<Proof> param) {
                return new ListCell<Proof>() {
                    @Override
                    protected void updateItem(Proof item, boolean empty) {
                        super.updateItem(item, empty);
                        if (empty) {
                            setText("");
                            setGraphic(null);
                        } else {
                            setText("Proof for " + item.filename);
                            ProgressBar bar = new ProgressBar();
                            bar.progressProperty().bind(item.depth.divide(3.0));
                            setGraphic(bar);
                        }
                    }
                };
            }
        });
    }

    public void onBitcoinSetup() {
        model.setWallet(bitcoin.wallet());
        addressControl.addressProperty().bind(model.addressProperty());
        balance.textProperty().bind(EasyBind.map(model.balanceProperty(), coin -> MonetaryFormat.BTC.noCode().format(coin).toString()));
        // Don't let the user click send money when the wallet is empty.
        sendMoneyOutBtn.disableProperty().bind(model.balanceProperty().isEqualTo(Coin.ZERO));
        /* ### no tor
        TorClient torClient = Main.bitcoin.peerGroup().getTorClient();
        if (torClient != null) {
            SimpleDoubleProperty torProgress = new SimpleDoubleProperty(-1);
            String torMsg = "Initialising Tor";
            syncItem = Main.instance.notificationBar.pushItem(torMsg, torProgress);
            torClient.addInitializationListener(new TorInitializationListener() {
                @Override
                public void initializationProgress(String message, int percent) {
                    Platform.runLater(() -> {
                        syncItem.label.set(torMsg + ": " + message);
                        torProgress.set(percent / 100.0);
                    });
                }

                @Override
                public void initializationCompleted() {
                    Platform.runLater(() -> {
                        syncItem.cancel();
                        showBitcoinSyncMessage();
                    });
                }
            });
        } else {
            showBitcoinSyncMessage();
        }
        */
        // ### just this
        showBitcoinSyncMessage();
        model.syncProgressProperty().addListener(x -> {
            if (model.syncProgressProperty().get() >= 1.0) {
                readyToGoAnimation();
                if (syncItem != null) {
                    syncItem.cancel();
                    syncItem = null;
                }
            } else if (syncItem == null) {
                showBitcoinSyncMessage();
            }
        });
    }

    private void showBitcoinSyncMessage() {
        syncItem = Main.instance.notificationBar.pushItem("Synchronising with the Bitcoin network", model.syncProgressProperty());
    }

    public void sendMoneyOut(ActionEvent event) {
        // Hide this UI and show the send money UI. This UI won't be clickable until the user dismisses send_money.
        Main.instance.overlayUI("send_money.fxml");
    }

    public void settingsClicked(ActionEvent event) {
        Main.OverlayUI<WalletSettingsController> screen = Main.instance.overlayUI("wallet_settings.fxml");
        screen.controller.initialize(null);
    }

    public void restoreFromSeedAnimation() {
        // Buttons slide out ...
        TranslateTransition leave = new TranslateTransition(Duration.millis(1200), controlsBox);
        leave.setByY(80.0);
        leave.play();
    }

    public void readyToGoAnimation() {
        // Buttons slide in and clickable address appears simultaneously.
        TranslateTransition arrive = new TranslateTransition(Duration.millis(1200), controlsBox);
        arrive.setInterpolator(new ElasticInterpolator(EasingMode.EASE_OUT, 1, 2));
        arrive.setToY(0.0);
        FadeTransition reveal = new FadeTransition(Duration.millis(1200), addressControl);
        reveal.setToValue(1.0);
        ParallelTransition group = new ParallelTransition(arrive, reveal);
        group.setDelay(NotificationBarPane.ANIM_OUT_DURATION);
        group.setCycleCount(1);
        group.play();
    }

    public DownloadProgressTracker progressBarUpdater() {
        return model.getDownloadProgressTracker();
    }

    public void onTimestampClicked(ActionEvent event) {
        // Ask the user for the document to timestamp
        File doc = new FileChooser().showOpenDialog(Main.instance.mainWindow);
        if (doc == null) return; // User cancelled
        try {
            timestamp(doc);
        } catch (IOException e) {
            crashAlert(e);
        } catch (InsufficientMoneyException e) {
            informationalAlert("Insufficient funds",
                    "You need bitcoins in this wallet in order to pay network fees.");
        }
    }

    private void timestamp(File doc) throws IOException, InsufficientMoneyException {
        // Hash it
        // ### Sha256Hash hash = Sha256Hash.hashFileContents(doc);
        Sha256Hash hash = Sha256Hash.of(doc);

        // Create a tx with an OP_RETURN output
        Transaction tx = new Transaction(Main.params);
        tx.addOutput(Coin.ZERO, ScriptBuilder.createOpReturnScript(hash.getBytes()));

        // Send it to the Bitcoin network
        // ### Main.bitcoin.wallet().sendCoins(Wallet.SendRequest.forTx(tx));
        // ### new 4 lines
        SendRequest sendRequest = SendRequest.forTx(tx);
        Coin coinsFee = Coin.valueOf(1000); // ### decrease the fee from 100000 to 1000
        sendRequest.feePerKb = coinsFee;  // ### decrease the fee from 100000 to 1000
        Wallet.SendResult sendResult = Main.bitcoin.wallet().sendCoins(sendRequest);

        // Add it to the UI list
        Proof proof = new Proof();
        // ### new
        proof.partialMerkleTreeFilled = false; // to prevent saving without partialMerkleTree
        proof.proofDepthMinimum = false; // to prevent saving without partialMerkleTree

        proof.tx = tx.bitcoinSerialize();
        proof.filename = doc.toString();
        pendingProofsList.getItems().add(proof);

        // ### new: additional saving data in textfile
        boolean filenameTimestampSaved = saveTimestamp(tx.getTxId().toString(), hash.toString(), doc, getActualDate(), Main.params.getId());
        if (filenameTimestampSaved == true) {
            informationalAlert("Timestamp Datei erstellt", (doc.toString()  + filenameTimestampAppend));
        } else {
            informationalAlert("Timestamp Datei NICHT erstellt", (doc.toString()  + filenameTimestampAppend));
        }

        // Grab the merkle branch when it appears in the block chain
        /* ### new EventListener
        Main.bitcoin.peerGroup().addEventListener(new AbstractPeerEventListener() {
            @Override
            public void onBlocksDownloaded(Peer peer, Block block, FilteredBlock filteredBlock, int blocksLeft) {
                List<Sha256Hash> hashes = new ArrayList<>();
                PartialMerkleTree tree = filteredBlock.getPartialMerkleTree();
                tree.getTxnHashAndMerkleRoot(hashes);
                if (hashes.contains(tx.getHash())) {
                    proof.partialMerkleTree = tree.bitcoinSerialize();
                    proof.blockHash = filteredBlock.getHash();
                }
            }
        });
        */
        // ### new listener
        Main.bitcoin.peerGroup().addBlocksDownloadedEventListener(new BlocksDownloadedEventListener() {
            @Override
            public void onBlocksDownloaded(Peer peer, Block block, @Nullable FilteredBlock filteredBlock, int i) {
                List<Sha256Hash> hashes = new ArrayList<>();
                // ### new to prevent saving without filled partialMerkleTree
                if (filteredBlock == null) {
                    if (block.hasTransactions()) {
                        List<Transaction> blockTransactions = block.getTransactions();
                        int blockTransactionsSize = blockTransactions.size();
                        String blockHash = block.getHashAsString();
                        //System.out.println("Downloaded BlockHash " + blockHash);
                        //System.out.println("blockTransactions Size: " + blockTransactionsSize);
                        for (int bt = 0; bt < blockTransactionsSize; bt++) {
                            Transaction blockTransaction = blockTransactions.get(bt);
                            Sha256Hash blockTransactionId = blockTransaction.getTxId();
                            //System.out.println("btx: " + bt + " " + blockTransactionId + " \n" + blockTransaction.toString());
                            // test for correct txId
                            int blockTransactionIdCompare = blockTransactionId.compareTo(tx.getTxId()); // compare should be 0
                            //System.out.println("blockTransactionIdInt " + blockTransactionIdCompare);
                            //System.out.println("blockTransactionId equals " + blockTransactionId.equals(tx.getTxId()));
                            if (blockTransactionIdCompare == 0) {
                                // found
                                proof.blockHash = block.getHash();
                                proof.partialMerkleTreeFilled = true;
                                if (proof.proofDepthMinimum == true) {
                                    // Save the proof to disk
                                    String filename = doc.toString() + ".timestamp";
                                    try {
                                        proof.saveTo(filename);
                                        // Remove it from the UI list
                                        pendingProofsList.getItems().remove(proof);
                                        // Notify the user that it's done
                                        informationalAlert("Proof complete", "Saved to " + filename);
                                    } catch (IOException e) {
                                        crashAlert(e);
                                    }
                                }
                            }
                        }
                    }
                } else {
                    PartialMerkleTree tree = filteredBlock.getPartialMerkleTree();
                    tree.getTxnHashAndMerkleRoot(hashes);
                    if (hashes.contains(tx.getTxId())) {
                        proof.partialMerkleTree = tree.bitcoinSerialize();
                        proof.blockHash = filteredBlock.getHash();
                        // ### new version 2 to prevent saving without filled partialMerkleTree
                        proof.partialMerkleTreeFilled = true;
                        if (proof.proofDepthMinimum == true) {
                            // Save the proof to disk
                            String filename = doc.toString() + ".timestamp";
                            try {
                                proof.saveTo(filename);
                                // Remove it from the UI list
                                pendingProofsList.getItems().remove(proof);
                                // Notify the user that it's done
                                informationalAlert("Proof complete", "Saved to " + filename);
                            } catch (IOException e) {
                                crashAlert(e);
                            }
                        }
                    }
                }
            }
        });
        // ### changed wait for confirmations from 3 to 1
        // ### Wait for confirmations (1)
        tx.getConfidence().addEventListener((confidence, reason) ->
        {
            if (confidence.getConfidenceType() != TransactionConfidence.ConfidenceType.BUILDING)
                return;
            proof.depth.set(confidence.getDepthInBlocks());
            // #### version 2 changed if (proof.depth.get() == 3) {
            // ### the problem is: Main.bitcoin.peerGroup().addBlocksDownloadedEventListener with
            //     onBlocksDownloaded is run AFTER this listener (tx.getConfidence().addEventListener)
            // that means that the field proof.partialMerkleTree = tree.bitcoinSerialize(); is
            // NOT filled with data and the verifying stopps with an NullPointerException
            // That sayed I seup a new save routine that takes care that all fields in proof are filled
            // with data before the saving tooks place
            if (proof.depth.get() == 1) { // ### changed version 2, one confirmation is enough
                // ### new version 2 to prevent saving without partialMerkelTree is filled
                proof.proofDepthMinimum = true;
                if (proof.partialMerkleTreeFilled == true) {
                    // Save the proof to disk
                    String filename = doc.toString() + ".timestamp";
                    try {
                        proof.saveTo(filename);
                        // Remove it from the UI list
                        pendingProofsList.getItems().remove(proof);
                        // Notify the user that it's done
                        informationalAlert("Proof complete", "Saved to " + filename);
                    } catch (IOException e) {
                        crashAlert(e);
                    }
                }
            }
        });
    }

    public void onVerifyClicked(ActionEvent event) {
        // Ask the user for the document to verify
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Timestamp proofs", "*.timestamp"));
        File proofFile = chooser.showOpenDialog(Main.instance.mainWindow);
        if (proofFile == null) return;  // User cancelled.
        // ### new
        String blockDate = "";
        try {
            blockDate = verifyNew(proofFile);
            if (!blockDate.equals("")) {
                GuiUtils.informationalAlert("Proof valid!", "Document existed at " + blockDate);
            } else {
                GuiUtils.informationalAlert("Proof was invalid", "");
            }
        } catch (IOException e) {
            e.printStackTrace();
            crashAlert(e);
        } catch (BlockStoreException e) {
            e.printStackTrace();
            crashAlert(e);
        } catch (ProofException e) {
            e.printStackTrace();
            GuiUtils.informationalAlert("Proof was invalid", e.getMessage());
        }

        /* ### org
        try {
            StoredBlock cursor = verify(proofFile);
            // Notify the user that the proof is valid and what the timestamp is
            informationalAlert("Proof valid!", "Document existed at " + cursor.getHeader().getTime());
        } catch (IOException | BlockStoreException e) {
            crashAlert(e);
        } catch (ProofException e) {
            informationalAlert("Proof was invalid", e.getMessage());
        }
         */
    }

    // ### new verifiing method
    private String verifyNew(File proofFile) throws IOException, BlockStoreException, ProofException {
        String dateTime = "";
        // Load the proof file
        Proof proof = Proof.readFrom(proofFile.getAbsolutePath());
        //ystem.out.println("proofFile gelesen: " + proof.toString());
        // Hash the document
        String docFile = proofFile.getAbsoluteFile().toString().replace(".timestamp", "");
        //### Sha256Hash hash = Sha256Hash.hashFileContents(new File(docFile));
        Sha256Hash fileHash = Sha256Hash.of(new File(docFile));
        byte[] txHashByteArray = proof.tx;
        Sha256Hash blockHash = proof.blockHash;
        // Find the block given the hash
        BlockStore blockStore = new MemoryBlockStore(params);
        BlockChain chain = null;
        try {
            chain = new BlockChain(params, blockStore);
        } catch (BlockStoreException e) {
            e.printStackTrace();
        }
        PeerGroup peerGroup = bitcoin.peerGroup();
        peerGroup.setUserAgent("Sample App", "1.0");
        try {
            peerGroup.getUseLocalhostPeerWhenPossible(); //###
            peerGroup.waitForPeers(1).get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        Peer peer = peerGroup.getConnectedPeers().get(0);
        Future<Block> future = peer.getBlock(blockHash);
        Block block = null;
        try {
            // ### block = future.get();
            block = future.get(20, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            // ### } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            System.err.println("Der Node hat kein Ergebnis innerhalb von 20 Sekunden abgeliefert, die Suche endet jetzt");
            return dateTime;
        }
        boolean txOpReturnFound = findTx(block, txHashByteArray, fileHash.toString());
        if (txOpReturnFound == true) {
            dateTime = block.getTime().toString();
        } else {
            // "Timestamp NICHT BESTÄTIGT - der Hash wurde NICHT in der Transaktion gefunden"
            GuiUtils.informationalAlert("Timestamp NOT approved - Hash was not in the transaction", "");
        }
        return dateTime;
    }

    // ### new - find the transactionId in downloaded block
    public boolean findTx(Block block, byte[] tfTxId, String tfHash) {
        boolean txFound = false;
        int txIdFound = 0;
        boolean txOpReturnFound = false;
        // get transactions
        List<Transaction> transactions = block.getTransactions();
        int transactionsSize = transactions.size();
        for (int i = 0; i < transactionsSize; i++) {
            Transaction tx = transactions.get(i);
            String txId = tx.getTxId().toString().toUpperCase();
            // *** new
            byte[] txIdByteArray = tx.bitcoinSerialize();
            if (Arrays.equals(txIdByteArray, tfTxId)) {
                //if (txId.equals(tfTxId.toUpperCase())) {
                txFound = true;
                txIdFound = i;
                break;
            }
        }
        if (txFound == true) {
            Transaction txWorks = transactions.get(txIdFound);
            List<TransactionOutput> txOutputs = txWorks.getOutputs();
            int txOutputsSize = txOutputs.size();
            for (int i = 0; i < txOutputsSize; i++) {
                TransactionOutput txOutput = txOutputs.get(i);
                Script txOutputScriptPubKey = txOutput.getScriptPubKey();
                Script.ScriptType txOutputScryptType = txOutputScriptPubKey.getScriptType();
                List<ScriptChunk> txOutputScriptPubKeyChunks = txOutputScriptPubKey.getChunks();
                int txOutputScriptPubKeyChunksSize = txOutputScriptPubKeyChunks.size();
                for (int j = 0; j < txOutputScriptPubKeyChunksSize; j++) {
                    ScriptChunk chunk = txOutputScriptPubKeyChunks.get(j);
                    byte[] chunkData = null;
                    try {
                        chunkData = chunk.data;
                        byte[] hashToFind = hexStringToByteArray(tfHash);
                        if (Arrays.equals(chunkData, hashToFind)) {
                            txOpReturnFound = true;
                            return txOpReturnFound;
                        }
                    } catch (NullPointerException e) {
                    }
                }
            }
        }
        return txOpReturnFound;
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

    // ### old verifiing method - left in sourcecode to show the changes
    /*
    private StoredBlock verify(File proofFile) throws IOException, ProofException, BlockStoreException {
        // Load the proof file
        Proof proof = Proof.readFrom(proofFile.getAbsolutePath());
        // Hash the document
        String docFile = proofFile.getAbsoluteFile().toString().replace(".timestamp", "");
        // ### Sha256Hash hash = Sha256Hash.hashFileContents(new File(docFile));
        Sha256Hash hash = Sha256Hash.of(new File(docFile));

        // Verify the hash is in the OP_RETURN output of the tx
        Transaction tx = new Transaction(Main.params, proof.tx);
        boolean found = false;
        for (TransactionOutput output : tx.getOutputs()) {
            // ### deprecated if (!output.getScriptPubKey().isOpReturn()) continue;
            if (!ScriptPattern.isOpReturn(output.getScriptPubKey())) continue; // new
            //noinspection ConstantConditions
            if (!Arrays.equals(output.getScriptPubKey().getChunks().get(1).data,
                    hash.getBytes()))
                throw new ProofException("Hash does not match OP_RETURN output");
            found = true;
            break;
        }
        if (!found) throw new ProofException("No OP_RETURN output in transaction");
        // Verify the transaction is in the Merkle proof
        // *** as the Regtest Mode does not provide a PartialMerkleTree AND the verifiaction should be done external we check the correct hash with the block instead of the wallet
        PartialMerkleTree tree = new PartialMerkleTree(Main.params, proof.partialMerkleTree, 0);
        List<Sha256Hash> hashes = new ArrayList<>();
        Sha256Hash merkleRoot = tree.getTxnHashAndMerkleRoot(hashes);
        // ### deprecated if (!hashes.contains(tx.getHash()))
        if (!hashes.contains(tx.getTxId())) // ### new
            throw new ProofException("Transaction not found in Merkle proof");
        // Find the block given the hash
        StoredBlock cursor = Main.bitcoin.chain().getChainHead();
        while (cursor != null && !cursor.getHeader().getHash().equals(proof.blockHash)) {
            cursor = cursor.getPrev(Main.bitcoin.store());
        }
        if (cursor == null)
            throw new ProofException("Could not find given block hash: " + proof.blockHash);

        // Verify the Merkle proof is linked to the block header
        if (!cursor.getHeader().getMerkleRoot().equals(merkleRoot))
            throw new ProofException("Merkle root does not match block header");
        return cursor;
    }
    */

    /* timestamp file format
        filenameTimestamp = filenameDoc + filenameTimestampAppend e.g. ".timestamp.txt"
        line 01: TxId
        line 02: Hash of filenameDoc
        line 03: filenameDoc
        line 04: length of filenameDoc
        line 05: date and time of timestamp (sendrequest)
        * method returns true if writing is successfull
        */
    // ### new save the timestamp as textfile, timestamp can be verified with
    // ### http://java-crypto.bplaced.net/bitcoinj-ueberpruefe-einen-timestamp-api/
    // ### github: https://github.com/java-crypto/BitcoinJTimestamp/tree/master/BitcoinJ%20Ueberpruefe%20einen%20Timestamp%20API
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
            writerSend.write("http://java-crypto.bplaced.net/bitcoinj-timestamp-mike-hearn-extended/" + "\n");
            writerSend.write("Sourcecode      : " + "https://github.com/java-crypto/BitcoinJTimestamp/tree/master/BitcoinJ%20Timestamp%20Mike%20Hearn%20Extended" + "\n");
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

    // ### new for date + time
    private static String getActualDate() {
        // provides the actual date and time in this format dd-MM-yyyy_HH-mm-ss e.g. 16-03-2020_10-27-15
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
        LocalDateTime today = LocalDateTime.now();
        return formatter.format(today);
    }

    // ### new get the "last modificated date of the file to hash for saving in timestamp-textfile
    public static String fileLastModified(File file) throws IOException {
        String fileLastModifiedDate = "";
        BasicFileAttributes bfa = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
        FileTime fileTime = bfa.lastModifiedTime();
        DateTimeFormatter DATE_FORMATTER_WITH_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss.SSS");
        fileLastModifiedDate = fileTime.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime().format(DATE_FORMATTER_WITH_TIME);
        return fileLastModifiedDate;
    }

    private class ProofException extends Exception {
        public ProofException(String s) {
            super(s);
        }
    }
}
