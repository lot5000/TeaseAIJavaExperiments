package me.goddragon.teaseai.utils.libraries.ripme.ripper;

import me.goddragon.teaseai.utils.TeaseLogger;
import me.goddragon.teaseai.utils.libraries.ripme.utils.Utils;
import org.jsoup.nodes.Document;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.logging.Level;

/**
 * Simplified ripper, designed for ripping from sites by parsing HTML.
 */
public abstract class AbstractHTMLRipper extends AlbumRipper {

    protected AbstractHTMLRipper(URL url) throws IOException {
        super(url);
    }

    protected abstract String getDomain();

    public abstract String getHost();

    protected abstract Document getFirstPage() throws IOException;

    public Document getNextPage(Document doc) throws IOException {
        return null;
    }

    protected abstract List<String> getURLsFromPage(Document page);

    protected List<String> getDescriptionsFromPage(Document doc) throws IOException {
        throw new IOException("getDescriptionsFromPage not implemented"); // Do I do this or make an abstract function?
    }

    protected abstract void downloadURL(URL url, int index);

    protected DownloadThreadPool getThreadPool() {
        return null;
    }

    protected boolean keepSortOrder() {
        return true;
    }

    @Override
    public boolean canRip(URL url) {
        return url.getHost().endsWith(getDomain());
    }

    @Override
    public URL sanitizeURL(URL url) throws MalformedURLException {
        return url;
    }

    protected boolean hasDescriptionSupport() {
        return false;
    }

    protected String[] getDescription(String url, Document page) throws IOException {
        throw new IOException("getDescription not implemented"); // Do I do this or make an abstract function?
    }

    protected int descSleepTime() {
        return 100;
    }

    protected List<String> getAlbumsToQueue(Document doc) {
        return null;
    }

    // If a page has Queue support then it has no images we want to download, just a list of urls we want to add to
    // the queue
    protected boolean hasQueueSupport() {
        return false;
    }

    // Takes a url and checks if it is for a page of albums
    protected boolean pageContainsAlbums(URL url) {
        return false;
    }

    @Override
    public void rip() throws IOException {
        int index = 0;
        int textindex = 0;
        LOGGER.log(Level.INFO, "Retrieving " + this.url);
        Document doc = getFirstPage();

        if (hasQueueSupport() && pageContainsAlbums(this.url)) {
            List<String> urls = getAlbumsToQueue(doc);

            // We set doc to null here so the while loop below this doesn't fire
            doc = null;
        }

        while (doc != null) {
            if (alreadyDownloadedUrls >= Utils.getConfigInteger("history.end_rip_after_already_seen", 1000000000) && !isThisATest()) {
                break;
            }
            List<String> imageURLs = getURLsFromPage(doc);
            // If hasASAPRipping() returns true then the ripper will handle downloading the files
            // if not it's done in the following block of code
            if (!hasASAPRipping()) {
                // Remove all but 1 image
                if (isThisATest()) {
                    while (imageURLs.size() > 1) {
                        imageURLs.remove(1);
                    }
                }

                if (imageURLs.isEmpty()) {
                    TeaseLogger.getLogger().log(Level.SEVERE, "No image found at " + doc.location());
                    throw new IOException("No images found at " + doc.location());
                }

                for (String imageURL : imageURLs) {
                    index += 1;
                    LOGGER.log(Level.FINE, "Found image url #" + index + ": " + imageURL);
                    downloadURL(new URL(imageURL), index);
                    if (isStopped()) {
                        break;
                    }
                }
            }
            if (hasDescriptionSupport() && Utils.getConfigBoolean("descriptions.save", false)) {
                LOGGER.log(Level.FINE, "Fetching description(s) from " + doc.location());
                List<String> textURLs = getDescriptionsFromPage(doc);
                if (!textURLs.isEmpty()) {
                    LOGGER.log(Level.FINE, "Found description link(s) from " + doc.location());
                    for (String textURL : textURLs) {
                        if (isStopped()) {
                            break;
                        }
                        textindex += 1;
                        LOGGER.log(Level.FINE, "Getting description from " + textURL);
                        String[] tempDesc = getDescription(textURL, doc);
                        if (tempDesc != null) {
                            if (Utils.getConfigBoolean("file.overwrite", false) || !(new File(
                                    workingDir.getCanonicalPath()
                                            + ""
                                            + File.separator
                                            + getPrefix(index)
                                            + (tempDesc.length > 1 ? tempDesc[1] : fileNameFromURL(new URL(textURL)))
                                            + ".txt").exists())) {
                                LOGGER.log(Level.FINE, "Got description from " + textURL);
                                saveText(new URL(textURL), "", tempDesc[0], textindex, (tempDesc.length > 1 ? tempDesc[1] : fileNameFromURL(new URL(textURL))));
                                sleep(descSleepTime());
                            } else {
                                LOGGER.log(Level.FINE, "Description from " + textURL + " already exists.");
                            }
                        }

                    }
                }
            }

            if (isStopped() || isThisATest()) {
                break;
            }

            try {
                doc = getNextPage(doc);
            } catch (IOException e) {
                LOGGER.log(Level.INFO, "Can't get next page: " + e.getMessage());
                break;
            }
        }
        TeaseLogger.getLogger().log(Level.INFO, "debug 123");
        // If they're using a thread pool, wait for it.
        if (getThreadPool() != null) {
            LOGGER.log(Level.FINE, "Waiting for threadpool " + getThreadPool().getClass().getName());
            getThreadPool().waitForThreads();
        }
        waitForThreads();
    }

    /**
     * Gets the file name from the URL
     *
     * @param url URL that you want to get the filename from
     * @return Filename of the URL
     */
    protected String fileNameFromURL(URL url) {
        String saveAs = url.toExternalForm();
        if (saveAs.substring(saveAs.length() - 1) == "/") {
            saveAs = saveAs.substring(0, saveAs.length() - 1);
        }
        saveAs = saveAs.substring(saveAs.lastIndexOf('/') + 1);
        if (saveAs.indexOf('?') >= 0) {
            saveAs = saveAs.substring(0, saveAs.indexOf('?'));
        }
        if (saveAs.indexOf('#') >= 0) {
            saveAs = saveAs.substring(0, saveAs.indexOf('#'));
        }
        if (saveAs.indexOf('&') >= 0) {
            saveAs = saveAs.substring(0, saveAs.indexOf('&'));
        }
        if (saveAs.indexOf(':') >= 0) {
            saveAs = saveAs.substring(0, saveAs.indexOf(':'));
        }
        return saveAs;
    }

    /**
     * @param url          Target URL
     * @param subdirectory Path to subdirectory where you want to save it
     * @param text         Text you want to save
     * @param index        Index in something like an album
     * @return True if ripped successfully
     * False if failed
     */
    public boolean saveText(URL url, String subdirectory, String text, int index) {
        String saveAs = fileNameFromURL(url);
        return saveText(url, subdirectory, text, index, saveAs);
    }

    protected boolean saveText(URL url, String subdirectory, String text, int index, String fileName) {
        // Not the best for some cases, like FurAffinity. Overridden there.
        try {
            stopCheck();
        } catch (IOException e) {
            return false;
        }
        File saveFileAs;
        try {
            if (!subdirectory.equals("")) { // Not sure about this part
                subdirectory = File.separator + subdirectory;
            }
            saveFileAs = new File(
                    workingDir.getCanonicalPath()
                            + subdirectory
                            + File.separator
                            + getPrefix(index)
                            + fileName
                            + ".txt");
            // Write the file
            FileOutputStream out = (new FileOutputStream(saveFileAs));
            out.write(text.getBytes());
            out.close();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "[!] Error creating save file path for description '" + url + "':", e);
            return false;
        }
        LOGGER.log(Level.FINE, "Downloading " + url + "'s description to " + saveFileAs);
        if (!saveFileAs.getParentFile().exists()) {
            LOGGER.log(Level.INFO, "[+] Creating directory: " + Utils.removeCWD(saveFileAs.getParent()));
            saveFileAs.getParentFile().mkdirs();
        }
        return true;
    }

    /**
     * Gets prefix based on where in the index it is
     *
     * @param index The index in question
     * @return Returns prefix for a file. (?)
     */
    protected String getPrefix(int index) {
        String prefix = "";
        if (keepSortOrder() && Utils.getConfigBoolean("download.save_order", true)) {
            prefix = String.format("%03d_", index);
        }
        return prefix;
    }
}
