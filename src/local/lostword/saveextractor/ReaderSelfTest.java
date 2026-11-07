package local.lostword.saveextractor;

import java.io.*;
import java.nio.*;
import java.util.*;
import org.json.*;

/** Headless synthetic layout checks; never opens an original game process. */
public final class ReaderSelfTest {
    static void require(boolean value){if(!value)throw new AssertionError("Synthetic reader check failed");}
    public static void main(String[] args)throws Exception {
        require(ReaderMain.isGamePackage("com.gg.lostword.mi"));
        require(ReaderMain.isGamePackage("com.gg.lostword.bilibili"));
        require(ReaderMain.isGamePackage("jp.goodsmile.touhoulostword_android"));
        require(!ReaderMain.isGamePackage("local.lostword.saveextractor"));
        require(!ReaderMain.isGamePackage("com.gg.lostword.mi;id"));
        ReaderMain r=new ReaderMain(args[0]);File temp=new File(args[1],"reader-self-test-memory.bin");
        require(ReaderMain.selectProfile(r.layout,"20b413be9b98db827cc84d6836c2e6fa98a6de3c221e6b1295044a4974f9ee43").getLong("user_type_rva")==72749120L);
        require(ReaderMain.selectProfile(r.layout,r.layout.getString("libil2cpp_sha256")).getLong("user_type_rva")==72753216L);
        require(ReaderMain.selectProfile(r.layout,"unknown")==null);
        ByteBuffer b=ByteBuffer.allocate(8192).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(0x100,-23);b.putLong(0x108,9007199254740993L);b.put(0x110,(byte)1);b.putFloat(0x114,0.25f);
        b.putLong(0x120,0x300);b.putInt(0x310,2);b.putChar(0x314,'测');b.putChar(0x316,'试');
        b.putLong(0x128,0x400);b.putLong(0x410,0x500);b.putInt(0x418,2);b.putLong(0x518,2);b.putInt(0x520,7);b.putInt(0x524,8);
        b.putLong(0x130,0x600);b.putLong(0x618,0x700);b.putInt(0x620,2);b.putLong(0x718,2);b.putInt(0x720,1);b.putInt(0x728,9);b.putInt(0x72c,11);b.putInt(0x730,-1);
        b.putLong(0x150,0x800);b.putLong(0x810,123L);b.putInt(0x818,456);b.putInt(0x81c,3);
        b.putLong(0x838,0x900);b.putLong(0x918,1);b.putLong(0x920,0xa00);
        b.put(0xa10,(byte)1);b.putInt(0xa14,2);b.putInt(0xa18,4);
        b.putLong(0x158,0xb00);b.putInt(0xb1c,42);
        try(FileOutputStream out=new FileOutputStream(temp)){out.write(b.array());}
        try(RandomAccessFile memory=new RandomAccessFile(temp,"r")){
            r.memory=memory;
            require(r.value(0x100,"int",0).equals(-23));require(r.value(0x108,"long",0).equals(9007199254740993L));
            require(r.value(0x110,"bool",0).equals(true));require(r.value(0x114,"float",0).equals(0.25));
            require(r.value(0x120,"string",0).equals("测试"));require(r.value(0x128,"List<int>",0).toString().equals("[7,8]"));
            require(r.value(0x130,"Dictionary<int, int>",0).toString().equals("{\"9\":11}"));
            require(r.value(0x140,"string",0)==JSONObject.NULL);
            JSONObject tower=(JSONObject)r.value(0x150,"TowerUnitData",0);
            require(tower.getLong("user_unit_id")==123L&&tower.getInt("hp")==456);
            require(tower.getJSONArray("barrierStates").getJSONObject(0).getInt("turn")==4);
            require(((JSONObject)r.value(0x158,"HeadListItem",0)).getInt("avatar_id")==42);
            boolean rejected=false;try{r.read(0,4);}catch(IOException expected){rejected=true;}require(rejected);
            rejected=false;try{r.value(0x100,"int",36);}catch(IOException expected){rejected=true;}require(rejected);
        }finally{temp.delete();}
        Set<String> primitives=new HashSet<>(Arrays.asList("int","uint","long","bool","float","string","PlayData.State","Dictionary<int, int>"));
        for(Iterator<String> types=r.schema.keys();types.hasNext();){String type=types.next();JSONObject fields=r.schema.getJSONObject(type);for(Iterator<String> names=fields.keys();names.hasNext();){String field=names.next(),name=field.toLowerCase(Locale.ROOT);require(!name.contains("token")&&!name.contains("password")&&!name.contains("session"));String kind=fields.getString(field);while(kind.startsWith("List<")||kind.endsWith("[]"))kind=kind.startsWith("List<")?kind.substring(5,kind.length()-1):kind.substring(0,kind.length()-2);require(primitives.contains(kind)||r.schema.has(kind));require(r.offsets.getJSONObject(type).has(field));}}
        System.out.println("READER_SELF_TEST_OK: numeric widths, UTF16, list, dictionary, null, bounds, schema filtering");
        System.exit(0);
    }
}
