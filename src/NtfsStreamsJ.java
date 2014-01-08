
import plugins.wdx.ContentPlugin;
import fun.*;
import seq.*;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.security.*;

/* Mark Russinovich's <a href="http://technet.microsoft.com/de-de/sysinternals/bb897440">streams.exe</a> v1.56
 * <p>
 * Frank Heyne's <a href="http://www.heysoft.de/en/software/lads.php?lang=EN">LADS.exe</a> v4.10
 * <p>
 * since Windows Vista: dir /r 
 */
public class NtfsStreamsJ extends ContentPlugin {

    public static enum Helper {
        STREAMS(
            "c:\\Programme\\totalcmd\\plugins\\wdx\\NtfsStreamsJ\\streams.exe",
            //"^\\s+:([^:]*):\\$DATA\\t(\\d+)$"
            "^\\s+:([^:]*):\\$DATA\\t(\\d+)|(([a-zA-z]:\\\\)?([^:?*|<>/]+\\\\)*([^:?*|<>/\\\\]+)):$",
            3, 1, 2
        ),
        LADS(
            "c:\\Programme\\totalcmd\\plugins\\wdx\\NtfsStreamsJ\\lads.exe",
            "^\\s*(\\d+)\\s+(.+?)\\\\?:([^:]*)$",
            2, 3, 1
        );
        
        public final String exeName;
        public final Pattern outputLinePattern;
        public final Fn1<Tuple3<String,String,String>, Tuple3<String,String,String>> fn_permute;

        
        Helper(String exeName, String pattern, int fileNameIdx, int streamNameIdx, int streamSizeIdx) {
            this.exeName = exeName;
            this.outputLinePattern = Pattern.compile(pattern);
            fn_permute = Tuple3.fn_permute(fileNameIdx - 1, streamNameIdx - 1, streamSizeIdx - 1); // -1 because group(0) is discarded
        }

        public SeqIterator<String> rawOutput(String fileName) throws IOException {
            ProcessBuilder pb = new ProcessBuilder(this.exeName, fileName);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            // ATTENTION: don't do p.waitFor() - it'd block forever if helper produces more output than the output stream's buffer can hold at once...
            final LineNumberReader r = new LineNumberReader(new InputStreamReader(p.getInputStream()));
            return new SeqIteratorAdapter<String>("linesFrom(" + pb.command() + ")") {
                protected String seekNext() {
                    try {
                        String line = r.readLine();
                        if (line != null) {
                            return line;
                        }
                        r.close();
                        return endOfSeq();
                    } catch (IOException e) {
                        //log.error(e);     // TODO: log(IOException) in rawOutput iterator
                        throw new RuntimeException(e);
                    }
                }
            };
        }

        public SeqIterator<Tuple3<String, String, String>> matchingLines(String fileName) throws IOException {
            return rawOutput(fileName)
                .map(Func.regexMatch(this.outputLinePattern))
                .filter(Predicate.notNull())
                .map(Func.toTuple3())
                .map(this.fn_permute)
            ;
        }
        
    }

    private final Helper helper;
    
    public NtfsStreamsJ() {
        this(Helper.LADS);
    }

    public NtfsStreamsJ(Helper helper) {
        log.debug(NtfsStreamsJ.class.getName() + "(" + helper + ")");
        this.helper = helper;

    }
    
    private File cachedFolder;
    private Map<File, List<AlternateDataStream>> cache = new HashMap<>();

    void clearCache() {
        cache.clear();
        cachedFolder = null;
    }
    
    void resetCache(File folder) {
        cache.clear();
        cachedFolder = folder;
    }

    public List<AlternateDataStream> getStreams(String fileName) throws IOException {
        final File file = new File(fileName).getCanonicalFile();
        fileName = file.getPath();
        final File folder = file.getParentFile();
        
        final SeqIterator<Tuple3<String, String, String>> matchingLines;
        List<List<AlternateDataStream>> lists;
        switch (this.helper) {
            case STREAMS:
                matchingLines = this.helper.matchingLines(file.getPath());
                break;
            case LADS:
                log.debug("folder=" + folder + ", cachedFolder=" + cachedFolder);
                if (!folder.equals(cachedFolder)) {
                    log.debug("making new cache...");
                    resetCache(folder);
                    matchingLines = this.helper.matchingLines(file.getParent());
                } else {
                    List<AlternateDataStream> result = Func.<List<AlternateDataStream>>elvis().apply(cache.get(file), Collections.<AlternateDataStream>emptyList());
                    log.debug("from cache: " + result.size());
                    return result;
                }
                break;
            default:
                throw new RuntimeException("NYI: " + this.helper);
        }

        Fn1<Tuple3<String, ?, ?>, String> proj0 = Tuple3.fn_project0();
        Fn1<Tuple3<?, String, ?>, String> proj1 = Tuple3.fn_project1();
        Fn1<Tuple3<?, ?, String>, String> proj2 = Tuple3.fn_project2();
        //System.out.println(Tuple2.fn_project0().apply(t));

        lists = matchingLines.sectionBy(
            Func.<String>elvis().compose(proj0)
            , Predicate.notNull().compose(proj1)
        )
        /*
        .filter(new Predicate<SectionIterator<String, ?>>() {
            public Boolean apply(SectionIterator<String, ?> s) {
                return new File(s.key()).equals(file);
            }
        })
        */
        .map(new Fn1<SectionIterator<String, Tuple3<String,String,String>>, List<AlternateDataStream>> () {
            public List<AlternateDataStream> apply(SectionIterator<String, Tuple3<String,String,String>> section) {
                List<AlternateDataStream> result = new ArrayList<AlternateDataStream>();
                String streamName;
                int streamLength;
                while (section.hasNext()) {
                    Tuple3<String, String, String> t = section.next();
                    streamName = t.item1;
                    streamLength = Integer.parseInt(t.item2, 10);
                    AlternateDataStream s = new AlternateDataStream(section.key(), streamName, streamLength);
                    result.add(s);
                    log.debug(s);
                }
                if (folder.equals(cachedFolder)) {
                    cache.put(new File(section.key()), result);
                }
                return result;
            }
        })
        .toList();
        if (folder.equals(cachedFolder)) {
            List<AlternateDataStream> result = Func.<List<AlternateDataStream>>elvis().apply(cache.get(file), Collections.<AlternateDataStream>emptyList());
            log.debug("from cache: " + result.size());
            return result;
        }
        /*
        for (List<AlternateDataStream> list: lists) {
            //if (list.file.equals(file)) {
                //System.out.println("KEY: " + list.key());// + "   " + list);
                for (AlternateDataStream s: list) {
                    System.out.println(s);
                }
            //}
        }
        */
        return lists.size() > 0 ? lists.get(0) : Collections.<AlternateDataStream>emptyList();
    }
    
    public int getCount(String fileName) throws IOException {
        return getStreams(fileName).size();
    }
    
    public String getSummary(String fileName) throws IOException {
        List<AlternateDataStream> streams = getStreams(fileName);
        int n = streams.size();
        if (n == 0) {
            return "";
        }
        StringBuilder result = new StringBuilder(n + " ADSs:");
        for (AlternateDataStream ads: streams) {
            result.append(System.lineSeparator()).append(ads);
        }
        result.append(System.lineSeparator());
        return result.toString();
    }

    private final Pattern md5ContentsPattern = Pattern.compile("^([0-9a-fA-F]{32})@([0-9]+)$");

    public String getMD5fromStream(File file) throws IOException {
        if (file.isDirectory()) {
            return null;
        }
        long lastModified = file.lastModified();
        AlternateDataStream md5ADS = new AlternateDataStream(file, "MD5");
        if (md5ADS.exists()) {
            String md5Contents = md5ADS.getContents();
            long time;
            // TODO: make regex match thread safe
            Matcher matcher = md5ContentsPattern.matcher(md5Contents);
            if (matcher.matches()) {
                String md5Str = matcher.group(1);
                String timeStr = matcher.group(2);
                time = Long.parseLong(timeStr, 10);
                if (lastModified <= time) {
                    return md5Str;
                } else {
                    log.warn("invalid :MD5 " + md5Contents + " on \"" + file.getPath() + "\" - outdated by " + (lastModified - time) + " ms");
                }
            } else {
                log.warn("invalid :MD5 " + md5Contents + " on \"" + file.getPath() + "\" - not matching /" + md5ContentsPattern + "/");
            }
        }
        return null;
    }
    
    
    public String getMD5(String fileName) throws IOException {
        File file = new File(fileName);
        if (file.isDirectory()) {
            return null;
        }
        String md5FromStream = getMD5fromStream(file);
        if (md5FromStream != null) {
            return md5FromStream;
        }

        MessageDigest md;
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            log.error(e);
            return "";
        }
        // TODO: lock file while calculating MD5
        long lastModified = file.lastModified();
        AlternateDataStream md5ADS = new AlternateDataStream(file, "MD5");
        FileInputStream fis = new FileInputStream(file);

        byte[] buffer = new byte[1024 * 1024];

        int nread = 0;
        while ((nread = fis.read(buffer)) != -1) {
            md.update(buffer, 0, nread);
        };
        fis.close();
        byte[] mdbytes = md.digest();
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < mdbytes.length; i++) {
            sb.append(Integer.toString((mdbytes[i] & 0xff) + 0x100, 16).substring(1));
        }
        String result = sb.toString();
        long t = System.currentTimeMillis();
        md5ADS.createIfNotExists().setContents(result + "@" + t);
        file.setLastModified(lastModified); // reset to original
        return result;
    }


    private void defineFixedStreamName(final String streamName) {
        define(new Field.STRING("stream_" + streamName.replace(".", "_")) {
            public String getValue(String fileName) throws IOException {
                AlternateDataStream s = new AlternateDataStream(fileName, streamName);
                if (!s.exists()) {
                    return null;
                }
                return s.getContents();
            }
        });
    
    }

    protected void initFields() {

        define(new EditableField.STRING("MD5") {
            public boolean isDelayInOrder(String fileName) throws IOException {
                File file = new File(fileName);
                if (file.isDirectory())
                    return false;
                if (file.length() < 1024 * 50)
                    return false;
                return getMD5fromStream(file) == null;
            }
            public String getValue(String fileName) throws IOException {
                return getMD5(fileName);
            }
            public void setValue(String fileName, String value) throws IOException {
                log.info("YES! (\"" + fileName + "\", " + value + ")");
            }
        });

        defineFixedStreamName("MD5");
        defineFixedStreamName("Zone.Identifier");
        defineFixedStreamName("\u0005DocumentSummaryInformation");
        defineFixedStreamName("\u0005SummaryInformation");
        defineFixedStreamName("\u0005OzngklrtOwudrp0bAayojd1qWh");
        defineFixedStreamName("\u0005SebiesnrMkudrfcoIaamtykdDa");

        define(new Field.INT("count") { public int getValue(String fileName) throws IOException {
            return getCount(fileName);
        }});

        define(new Field.STRING("summary") { public String getValue(String fileName) throws IOException {
            return getSummary(fileName);
        }});

    }

}