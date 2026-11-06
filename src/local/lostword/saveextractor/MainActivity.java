package local.lostword.saveextractor;

import android.app.Activity;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;
import java.lang.Process;

public final class MainActivity extends Activity {
    TextView state,summary;Button extract,save;ProgressBar progress;
    final Handler handler=new Handler(Looper.getMainLooper());boolean busy=false;
    File output,status;
    int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
    TextView text(String content,int size,int color){TextView t=new TextView(this);t.setText(content);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,dp(8),0,dp(8));return t;}
    @Override public void onCreate(Bundle b){super.onCreate(b);
        output=new File(getFilesDir(),"original-save.json");status=new File(getFilesDir(),"capture-status.txt");
        getWindow().setStatusBarColor(Color.rgb(35,65,65));
        ScrollView scroll=new ScrollView(this);LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(dp(24),dp(40),dp(24),dp(28));page.setBackgroundColor(Color.rgb(247,246,240));scroll.addView(page);
        page.addView(text("归言录 · 存档提取",27,Color.rgb(35,65,65)));
        page.addView(text("把原版的旅程，带到自己的服务器。",16,Color.DKGRAY));
        page.addView(text("1  设备开启 Root，并允许本工具使用 Root。\n2  手动打开原版 B服，登录到主页后保持不操作。\n3  返回这里提取，保存 JSON 文件，再到私服账号页面导入。",17,Color.rgb(45,55,55)));
        page.addView(text("支持：已验证的 B服 6.6.0 · ARM64\n仅读取当前已同步到客户端的玩家数据。未同步的服务端历史不在提取范围内。",14,Color.DKGRAY));
        extract=new Button(this);extract.setText("读取原版存档");page.addView(extract);
        progress=new ProgressBar(this);progress.setVisibility(View.GONE);page.addView(progress);
        state=text("等待提取",16,Color.rgb(35,65,65));page.addView(state);
        summary=text("",16,Color.DKGRAY);page.addView(summary);
        save=new Button(this);save.setText("保存存档文件…");save.setEnabled(false);page.addView(save);
        page.addView(text("全部在本机完成。工具不申请联网权限，也不读取账号密码或登录令牌。\n\nMuMu 如果只有 ADB Root、没有 su，请使用随附的电脑辅助提取脚本。",14,Color.GRAY));
        setContentView(scroll);extract.setOnClickListener(v->capture());save.setOnClickListener(v->{Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"lostword-original-"+System.currentTimeMillis()+".json");startActivityForResult(intent,1);});
        refresh();
    }
    static String quote(String s){return "'"+s.replace("'","'\\''")+"'";}
    Process root(String command)throws IOException{return new ProcessBuilder("su","-c",command).redirectErrorStream(true).start();}
    void capture(){if(busy)return;busy=true;extract.setEnabled(false);save.setEnabled(false);summary.setText("");progress.setVisibility(View.VISIBLE);state.setText("正在请求 Root 权限…");
        new Thread(()->{try{
            if(output.exists()&&!output.delete())throw new IOException("无法清理上一次临时文件，请使用电脑辅助提取");
            if(status.exists())status.delete();
            Process probe=root("id");if(!probe.waitFor(30,TimeUnit.SECONDS)){probe.destroy();throw new IOException("Root 授权等待超时，请授权后重试");}
            String identity=new String(ReaderMain.readAll(probe.getInputStream(),8192),StandardCharsets.UTF_8);
            if(probe.exitValue()!=0||!identity.contains("uid=0"))throw new IOException("未获得 Root。请允许授权；MuMu 无 su 时请使用电脑辅助脚本");
            String apk=getPackageCodePath();Process task=root("CLASSPATH="+quote(apk)+" /system/bin/app_process /system/bin local.lostword.saveextractor.ReaderMain "+quote(apk)+" "+quote(output.getAbsolutePath()));
            long deadline=System.currentTimeMillis()+180000;
            while(!task.waitFor(500,TimeUnit.MILLISECONDS)){
                handler.post(this::refreshStatus);
                if(System.currentTimeMillis()>deadline){task.destroy();throw new IOException("提取超时，请确认原版仍停在主页后重试");}
            }
            if(task.exitValue()!=0){String reason=status.exists()?new String(Files.readAllBytes(status.toPath()),StandardCharsets.UTF_8):"Root 读取失败；设备策略可能禁止读取原版进程，请使用电脑辅助方式";throw new IOException(reason);}
            handler.post(()->{state.setText("提取完成，请保存存档文件");refresh();});
        }catch(Exception e){String reason=e instanceof FileNotFoundException?"未找到 su，请使用电脑辅助提取脚本":e.getMessage();handler.post(()->state.setText("提取未完成："+reason));}
        finally{handler.post(()->{busy=false;extract.setEnabled(true);progress.setVisibility(View.GONE);getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);});}
        },"save-extractor").start();getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    void refreshStatus(){try{if(status.exists())state.setText(new String(Files.readAllBytes(status.toPath()),StandardCharsets.UTF_8));}catch(Exception ignored){}}
    void refresh(){try{if(!output.isFile())return;JSONObject archive=new JSONObject(new String(Files.readAllBytes(output.toPath()),StandardCharsets.UTF_8));String json=archive.getString("user_json");if(!ReaderMain.sha(json.getBytes(StandardCharsets.UTF_8)).equals(archive.getString("sha256")))throw new IOException("文件校验失败");JSONObject user=new JSONObject(json);summary.setText(user.getString("player_name")+"  ·  等级 "+user.getInt("level")+"\n角色 "+user.getJSONObject("unit").getJSONArray("unit_list").length()+"  ·  绘卷 "+user.getJSONObject("picture").getJSONArray("picture_list").length());save.setEnabled(true);}catch(Exception e){save.setEnabled(false);state.setText("临时存档无效，请重新提取");}}
    @Override protected void onResume(){super.onResume();if(output!=null&&!busy)refresh();}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==1&&result==RESULT_OK&&data!=null){Uri uri=data.getData();if(uri==null)return;try(InputStream in=new FileInputStream(output);OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException("无法打开保存位置");byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);state.setText("已保存。请在私服账号页面选择存档位并导入该 JSON 文件。");}catch(Exception e){state.setText("保存失败："+e.getMessage());}}}
}
