import kotlin.text.Regex;
import okhttp3.*;
import okhttp3.brotli.BrotliInterceptor;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class TiktokHandler {
    private final HashMap<String, List<Cookie>> cookieStore;
    private final OkHttpClient client;
    final Regex regex = new Regex("\"downloadAddr\":\"(.+?)\"");
    final String url;

    public TiktokHandler(String url) {
        this.url = url;
        this.cookieStore = new HashMap<>();
        this.client = new OkHttpClient.Builder()
                //.proxy(new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("127.0.0.1", 10808)))
                .addInterceptor(BrotliInterceptor.INSTANCE)  // 对应Accept-Encoding的"br"
                .cookieJar(new CookieJar() {
                    @Override
                    public void saveFromResponse(@NotNull HttpUrl httpUrl, @NotNull List<Cookie> list) {
                        cookieStore.put(httpUrl.topPrivateDomain(), list);
                    }

                    @NotNull
                    @Override
                    public List<Cookie> loadForRequest(@NotNull HttpUrl httpUrl) {
                        List<Cookie> cookies = cookieStore.get(httpUrl.topPrivateDomain());
                        return cookies != null ? cookies : new ArrayList<>();
                    }
                })
                .build();
    }

    private File downloadVideo(String videoUrl) throws IOException {
        Request req = new Request.Builder()
                .url(videoUrl)
                .addHeader("Accept-Encoding", "identity;q=1, *;q=0")
                .addHeader("Range", "bytes=0-")
                .addHeader("Referer", "https://www.tiktok.com/")
                .build();
        Response res = client.newCall(req).execute();
        if(res.code()!=200 && res.code()!=206) {
            System.out.println(res.code()+" "+res.message());
            res.close();
            return null;
        } else {
            byte[] video = res.body().bytes();
            res.close();
            File file = new File("./"+System.currentTimeMillis()+".mp4");
            file.createNewFile();
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(video);
            fos.close();
            return file;
        }
    }

    public File fetchVideo() throws IOException {
        Request request = new Request.Builder()
                .url(this.url)
                .addHeader("Upgrade-Insecure-Requests", "1")
                .addHeader("Accept", "*/*")
                .addHeader("DNT", "1")
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko)")
                .build();
        Response response = client.newCall(request).execute();
        if(response.code()!=200) {
            System.out.println(response.code()+" "+response.message());
            response.close();
            return null;
        } else {
            String text = response.body().string();
            response.close();
            String videoUrl = regex.find(text,0).getGroups().get(1).getValue().replace("\\u0026","&");
            System.out.println(videoUrl);
            return downloadVideo(videoUrl);
        }
    }

    public static void main(String[] args) {
        TiktokHandler tt = new TiktokHandler("https://vt.tiktok.com/ZSsmPx3A/");
        try {
            tt.fetchVideo();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
}
