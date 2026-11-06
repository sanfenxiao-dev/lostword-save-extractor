package local.lostword.saveextractor;

import org.json.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;

/** Root helper: selected gameplay fields only, no injection or game writes. */
public final class ReaderMain {
    static final String PACKAGE="com.gg.lostword.bilibili";
    static final String[] CORE={"player_name","level","exp","unit","picture","item","team","quest_playdata","home_unit_info","ability_seal"};
    JSONObject layout,schema,offsets;
    RandomAccessFile memory;
    Map<Long,byte[]> pages=new HashMap<>();
    Set<String> visiting=new HashSet<>();
    int objects;

    static byte[] readAll(InputStream in,int limit)throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[65536];int n;
        while((n=in.read(buf))!=-1){if(out.size()+n>limit)throw new IOException("数据超过安全上限");out.write(buf,0,n);}return out.toByteArray();
    }
    static String sha(byte[] bytes)throws Exception{return hex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    static String hex(byte[] data){StringBuilder b=new StringBuilder();for(byte x:data)b.append(String.format(Locale.ROOT,"%02x",x&255));return b.toString();}
    static String hashFile(String path)throws Exception {
        MessageDigest d=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(path)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)d.update(b,0,n);}return hex(d.digest());
    }
    static void writeAtomic(File file,String text)throws Exception {
        File tmp=new File(file.getPath()+".tmp");
        try(FileOutputStream out=new FileOutputStream(tmp)){out.write(text.getBytes(StandardCharsets.UTF_8));out.getFD().sync();}
        if(!tmp.renameTo(file))throw new IOException("无法保存导出文件");file.setReadable(true,false);
    }
    static void status(File out,String text)throws Exception{writeAtomic(new File(out.getParentFile(),"capture-status.txt"),text);}
    ReaderMain(String apk)throws Exception {
        try(ZipFile zip=new ZipFile(apk);InputStream in=zip.getInputStream(zip.getEntry("assets/layout.json"))){layout=new JSONObject(new String(readAll(in,4*1024*1024),StandardCharsets.UTF_8));}
        schema=layout.getJSONObject("schema");offsets=layout.getJSONObject("offsets");
    }
    byte[] read(long address,int length)throws Exception {
        if(address<=0||length<0||length>4*1024*1024)throw new IOException("内存范围无效");
        byte[] out=new byte[length];int copied=0;
        while(copied<length){long a=address+copied,base=a&~4095L;int offset=(int)(a-base),n=Math.min(4096-offset,length-copied);byte[] page=pages.get(base);
            if(page==null){if(pages.size()>32768)throw new IOException("读取范围过大");page=new byte[4096];memory.seek(base);memory.readFully(page);pages.put(base,page);}
            System.arraycopy(page,offset,out,copied,n);copied+=n;
        }return out;
    }
    ByteBuffer bytes(long address,int length)throws Exception{return ByteBuffer.wrap(read(address,length)).order(ByteOrder.LITTLE_ENDIAN);}
    long ptr(long address)throws Exception{return bytes(address,8).getLong();}
    int integer(long address)throws Exception{return bytes(address,4).getInt();}
    String string(long address)throws Exception{int n=integer(address+16);if(n<0||n>1000000)throw new IOException("字符串长度无效");return new String(read(address+20,n*2),StandardCharsets.UTF_16LE);}
    int size(String type){switch(type){case "bool":return 1;case "int":case "uint":case "float":case "PlayData.State":return 4;default:return 8;}}
    Object value(long address,String type,int depth)throws Exception {
        if(depth>35)throw new IOException("数据嵌套过深");
        switch(type){case "int":case "PlayData.State":return integer(address);case "uint":return integer(address)&0xffffffffL;case "long":return ptr(address);case "bool":return read(address,1)[0]!=0;case "float":float f=bytes(address,4).getFloat();if(Float.isNaN(f)||Float.isInfinite(f))throw new IOException("数值无效");return (double)f;}
        long p=ptr(address);if(p==0)return JSONObject.NULL;
        if(type.equals("string"))return string(p);
        if(type.equals("Dictionary<int, int>")){
            long entries=ptr(p+24);int count=integer(p+32);checkCount(count);JSONObject result=new JSONObject();
            if(count>0&&(entries==0||ptr(entries+24)<count))throw new IOException("字典数据无效");
            for(int i=0;i<count;i++){ByteBuffer b=bytes(entries+32+16L*i,16);int hash=b.getInt();b.getInt();int key=b.getInt(),val=b.getInt();if(hash>=0)result.put(Integer.toString(key),val);}return result;
        }
        if(type.startsWith("List<")||type.endsWith("[]")){
            boolean list=type.startsWith("List<");String inner=list?type.substring(5,type.length()-1):type.substring(0,type.length()-2);
            long array=list?ptr(p+16):p;long length=list?integer(p+24):ptr(p+24);checkCount(length);
            if(length>0&&(array==0||ptr(array+24)<length))throw new IOException("列表数据无效");
            JSONArray result=new JSONArray();for(int i=0;i<length;i++)result.put(value(array+32+(long)i*size(inner),inner,depth+1));return result;
        }
        if(schema.has(type))return object(p,type,depth+1);
        throw new IOException("不支持的字段类型："+type);
    }
    static void checkCount(long count)throws IOException{if(count<0||count>200000)throw new IOException("集合长度无效");}
    JSONObject object(long pointer,String type,int depth)throws Exception {
        if(++objects>200000)throw new IOException("对象数量过大");String key=pointer+":"+type;
        if(!visiting.add(key))throw new IOException("数据出现循环引用");
        JSONObject result=new JSONObject(),fields=schema.getJSONObject(type),locations=offsets.getJSONObject(type);
        Iterator<String> it=fields.keys();while(it.hasNext()){String name=it.next();result.put(name,value(pointer+locations.getInt(name),fields.getString(name),depth+1));}
        visiting.remove(key);return result;
    }
    JSONObject capture(int pid,List<Long> bases)throws Exception {
        pages.clear();visiting.clear();objects=0;
        try(RandomAccessFile file=new RandomAccessFile("/proc/"+pid+"/mem","r")){
            memory=file;long user=0;
            for(long base:bases){try{long klass=ptr(base+layout.getLong("user_type_rva")),name=ptr(klass+16);byte[] label=read(name,5);
                if(!Arrays.equals(label,new byte[]{85,115,101,114,0}))continue;
                long statics=ptr(klass+184),candidate=ptr(statics);if(candidate>0&&ptr(candidate)==klass){user=candidate;break;}
            }catch(IOException ignored){}}
            if(user==0)throw new IOException("尚未找到玩家数据，请让原版停在登录后的主页");
            JSONObject result=object(user,"UserImpl",0);validate(result);return result;
        }finally{memory=null;pages.clear();}
    }
    static void validate(JSONObject user)throws Exception {
        if(user.optInt("level",0)<1||user.optInt("level",0)>1000||user.optString("player_name","").isEmpty())throw new IOException("玩家核心数据不完整");
        for(String[] pair:new String[][]{{"unit","unit_list","user_unit_id"},{"picture","picture_list","user_picture_id"}}){
            JSONArray list=user.getJSONObject(pair[0]).getJSONArray(pair[1]);if(list.length()==0)throw new IOException("角色或绘卷数据尚未同步");
            Set<Long> ids=new HashSet<>();for(int i=0;i<list.length();i++){long id=list.getJSONObject(i).getLong(pair[2]);if(id<=0||!ids.add(id))throw new IOException("实例数据重复或无效");}
        }
        for(String section:new String[]{"item","team","home_unit_info","quest_playdata"})if(!user.has(section)||user.isNull(section))throw new IOException("缺少存档分区："+section);
    }
    static String stable(Object value)throws Exception {
        if(value instanceof JSONObject){JSONObject obj=(JSONObject)value;List<String> keys=new ArrayList<>();Iterator<String> it=obj.keys();while(it.hasNext())keys.add(it.next());Collections.sort(keys);StringBuilder b=new StringBuilder("{");for(String key:keys){if(b.length()>1)b.append(',');b.append(JSONObject.quote(key)).append(':').append(stable(obj.get(key)));}return b.append('}').toString();}
        if(value instanceof JSONArray){JSONArray list=(JSONArray)value;StringBuilder b=new StringBuilder("[");for(int i=0;i<list.length();i++){if(i>0)b.append(',');b.append(stable(list.get(i)));}return b.append(']').toString();}
        return value==null||value==JSONObject.NULL?"null":value instanceof String?JSONObject.quote((String)value):value.toString();
    }
    void extract(File out)throws Exception {
        status(out,"正在查找原版 B服进程…");
        Process proc=new ProcessBuilder("/system/bin/pidof",PACKAGE).start();String result=new String(readAll(proc.getInputStream(),1024),StandardCharsets.UTF_8).trim();proc.waitFor();
        if(!result.matches("[0-9]+"))throw new IOException("请手动打开原版 B服并进入主页，再回来提取");int pid=Integer.parseInt(result);
        String maps=new String(Files.readAllBytes(new File("/proc/"+pid+"/maps").toPath()),StandardCharsets.UTF_8);List<Long> bases=new ArrayList<>();String lib=null;
        for(String line:maps.split("\n")){String[] parts=line.trim().split("\\s+",6);if(parts.length==6&&parts[5].endsWith("/libil2cpp.so")&&Long.parseLong(parts[2],16)==0){bases.add(Long.parseLong(parts[0].split("-")[0],16));lib=parts[5];}}
        if(lib==null)throw new IOException("未找到支持的 64 位原版客户端，请确认已进入主页");
        status(out,"正在核对原版程序版本…");
        if(!hashFile(lib).equals(layout.getString("libil2cpp_sha256")))throw new IOException("此原版程序版本尚不支持，已停止读取；当前仅支持验证过的 B服 6.6.0 ARM64");
        status(out,"正在读取第一份玩家快照…");JSONObject first=capture(pid,bases);
        Thread.sleep(750);status(out,"正在读取第二份快照并核对一致性…");JSONObject second=capture(pid,bases);
        for(String field:CORE)if(!stable(first.opt(field)).equals(stable(second.opt(field))))throw new IOException("读取期间玩家数据发生变化，请停在主页等待片刻后重试");
        String userJson=stable(second);if(userJson.getBytes(StandardCharsets.UTF_8).length>24*1024*1024)throw new IOException("存档过大");
        JSONObject archive=new JSONObject().put("format","lostword-original-capture-v1").put("source_client","bilibili-6.6.0-arm64")
            .put("user_json",userJson).put("sha256",sha(userJson.getBytes(StandardCharsets.UTF_8))).put("created_at_ms",System.currentTimeMillis())
            .put("core_double_read_verified",true).put("original_client_modified",false).put("server_requests_sent",false);
        writeAtomic(out,archive.toString());status(out,"提取完成。请保存文件，再到私服账号页面导入存档位。");
    }
    public static void main(String[] args){
        if(args.length!=2){System.err.println("Expected APK and output path");System.exit(2);}
        File out=new File(args[1]);
        try{new ReaderMain(args[0]).extract(out);}catch(Exception e){try{status(out,"提取未完成："+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage()));}catch(Exception ignored){}System.err.println("EXTRACTION_FAILED: "+e.getClass().getSimpleName());System.exit(1);}
        System.exit(0);
    }
}
