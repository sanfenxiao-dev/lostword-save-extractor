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
        ByteBuffer b=ByteBuffer.allocate(8192).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(0x100,-23);b.putLong(0x108,9007199254740993L);b.put(0x110,(byte)1);b.putFloat(0x114,0.25f);
        b.putLong(0x120,0x300);b.putInt(0x310,2);b.putChar(0x314,'测');b.putChar(0x316,'试');
        b.putLong(0x128,0x400);b.putLong(0x410,0x500);b.putInt(0x418,2);b.putLong(0x518,2);b.putInt(0x520,7);b.putInt(0x524,8);
        b.putLong(0x130,0x600);b.putLong(0x618,0x700);b.putInt(0x620,2);b.putLong(0x718,2);b.putInt(0x720,1);b.putInt(0x728,9);b.putInt(0x72c,11);b.putInt(0x730,-1);
        try(FileOutputStream out=new FileOutputStream(temp)){out.write(b.array());}
        try(RandomAccessFile memory=new RandomAccessFile(temp,"r")){
            r.memory=memory;
            require(r.value(0x100,"int",0).equals(-23));require(r.value(0x108,"long",0).equals(9007199254740993L));
            require(r.value(0x110,"bool",0).equals(true));require(r.value(0x114,"float",0).equals(0.25));
            require(r.value(0x120,"string",0).equals("测试"));require(r.value(0x128,"List<int>",0).toString().equals("[7,8]"));
            require(r.value(0x130,"Dictionary<int, int>",0).toString().equals("{\"9\":11}"));
            require(r.value(0x140,"string",0)==JSONObject.NULL);
            boolean rejected=false;try{r.read(0,4);}catch(IOException expected){rejected=true;}require(rejected);
            rejected=false;try{r.value(0x100,"int",36);}catch(IOException expected){rejected=true;}require(rejected);
        }finally{temp.delete();}
        for(Iterator<String> types=r.schema.keys();types.hasNext();){JSONObject fields=r.schema.getJSONObject(types.next());for(Iterator<String> names=fields.keys();names.hasNext();){String name=names.next().toLowerCase(Locale.ROOT);require(!name.contains("token")&&!name.contains("password")&&!name.contains("session"));}}
        System.out.println("READER_SELF_TEST_OK: numeric widths, UTF16, list, dictionary, null, bounds, schema filtering");
        System.exit(0);
    }
}
