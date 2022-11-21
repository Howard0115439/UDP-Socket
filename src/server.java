import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.concurrent.*;

public class server
{
    private static DatagramSocket st = null;
    private static String folderPathString = "";
    private static Path folderPath = null;

    private static class Task implements Callable<Void>
    {
        private DatagramPacket packet;
        private byte[] buf;
        private byte[] signBuf;
        private RandomAccessFile file;
        private String adress;

        Task(DatagramPacket packet, byte[] buf)
        {
            this.packet = packet;               //將接收到的資料存入packet
            this.buf = buf;                     //將接收到的資料存入buf
            this.signBuf = new byte[1024];
            this.adress = this.packet.getAddress().getHostAddress() + ":" + this.packet.getPort();          //getAddress返回byte型式的IP address，getHostAddress返回string型式的IP address
        }

        @Override
        public Void call() throws Exception
        {
            Thread.currentThread().setPriority(10);                                         //將現在的thread設成最高優先權

            try
            {
                String command = new String(buf);                                           //將buf從byte[]型式轉成string
                command = command.replace("\0", "");                       //用""取代"\0"，把buf在byte[]裡面的"\0"清除
                System.out.println("Command received : " + command + " from " + adress);

                if (command.equals("index"))
                {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Folder Path : " + folderPath.toAbsolutePath().toString() + "\n");        //toAbsolutePath()表示path的絕對路徑
                    try (DirectoryStream<Path> stream = Files.newDirectoryStream(folderPath))           //先存到類似arraylist的DirectoryStream<Path>再遍歷目錄
                    {
                        for (Path p : stream)
                        {
                            sb.append(p.getFileName() + "\n");
                        }
                        String message = sb.toString();
                        System.out.print(message);
                        packet.setData(message.getBytes(), 0, message.getBytes().length);           //getBytes()將message轉成byte[]型式
                        st.send(packet);                                                                  //將packet資料發送到client

                    }
                    catch (IOException e)
                    {
                        System.err.println("Invalid folder path.");
                        e.printStackTrace();
                    }
                }
                else if (command.startsWith("get "))
                {
                    String filePathString = command.split(" ")[1];                              //輸入為get test1.txt,所以command.split(" ")[0]是get,command.split(" ")[1]是test1.txt
                    Path filePath = Paths.get(folderPathString + "/" + filePathString);
                    if (Files.notExists(filePath))
                    {
                        packet.setData("error\nInvalid file name.".getBytes(), 0, "error\nInvalid file name.".getBytes().length);       //getBytes()將字串轉成byte[]型式
                        st.send(packet);                                                                                                      //將packet資料發送到client
                        System.err.println("Invalid file name.");
                    }
                    else
                    {
                        file = new RandomAccessFile(folderPathString + "/" + filePathString, "r");      //RandomAccessFile()可用seek()來尋找東西，也能用read()來讀取東西，"r" 代表只能讀取，"rw"代表能讀取也能寫入
                        int packetCount = 1;
                        int reciveSize = -1;

                        while ((reciveSize = file.read(buf)) != -1)                     //*****read()用來讀取byte[]資料，一次讀一行 ，讀取結束會return -1，如果buf裡面有資料的話(buf存txt檔裡面的資料)*********
                        {
                            packet.setData(buf, 0, reciveSize);                   //就將buf面的資料傳送給client
                            st.send(packet);
                            // wait for successful respons
                            while (true)
                            {
                                packet.setData(signBuf, 0, signBuf.length);
                                st.receive(packet);                                     //如果packet接收到"successful"表示傳送成功，就將"successful"存到signBuf(),client.java(67行)
                                String reply = new String(signBuf);
                                reply = reply.replace("\0", "");        //將signBuf剩餘位置的"\0"轉成""
                                if (reply.equals("successful"))                               //接收成功就跳出迴圈
                                {
                                    break;
                                }
                                else                                                    //接收失敗就重新接收，所以就不會跑到read()(server.java-77行)換到txt檔裡面的下一行資料
                                {
                                    System.out.println("sent packet again " + packetCount);
                                    packet.setData(buf, 0, reciveSize);             //和server.java-79行相同
                                    st.send(packet);
                                }
                            }
                            packetCount++;
                        }
                        while (true)                                                                      //txt檔裡面全部字串傳送完成，再傳送exit字串
                        {
                            System.out.println("Send exit sign");
                            packet.setData("exit".getBytes(), 0, "exit".getBytes().length);         //成功接收完packet(client.java-88行:"successful")並送出exit資訊給client請求結束
                            st.send(packet);

                            packet.setData(signBuf, 0, signBuf.length);                             //從client接收到exit確認結束，並將exist存入signBuf
                            st.receive(packet);
                            // exit
                            String reply = new String(signBuf);                                             //將signBuf從byte[]轉成string
                            reply = reply.replace("\0", "");

                            if (reply.equals("exit"))
                            {
                                break;
                            }
                            else
                            {
                                System.out.println("sent exit sign again");                                 //重新寄送exit給client
                                packet.setData("exit".getBytes(), 0, "exit".getBytes().length);
                                st.send(packet);
                            }
                        }

                    }
                }
                else
                {
                    packet.setData("error\nInvalid file name.".getBytes(), 0, "error\nInvalid file name.".getBytes().length);       //getBytes()將字串轉成byte[]型式
                    st.send(packet);                                                                                                      //將packet資料發送到client
                    System.err.println("Invalid command.");
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
            return null;
        }
    }

    public static void main(String[] args)
    {
//        if (args.length != 1)                                           //cmd no folder path input, so args.length==0
//        {
//            System.err.println("Please input the folder path.");
//            return;
//        }
//        String folderPathString = args[0];
        folderPathString = "C:\\Users\\user\\IdeaProjects\\network_programming_M4\\data";
        folderPath = Paths.get(folderPathString);                  //convert string to the path
        if (!Files.isDirectory(folderPath))                             //isDirectory()判斷是否為路徑，是return true，否return false
        {
            System.err.println("Invalid folder path.");
            return;
        }

        ExecutorService pool = Executors.newFixedThreadPool(50);    //产生一个 ExecutorService 对象，这个对象带有一个大小为 poolSize 的线程池，若任务数量大于 poolSize ，任务会被放在一个 queue 里顺序执行。

        try
        {
//            ServerSocket ss = new ServerSocket(8089);              //創建server socket
//            System.out.println("wait for connected");
//            Socket st = ss.accept();                                    //accept()等待連接請求
//            System.out.println("Server connected");
//            BufferedReader input = new BufferedReader(new InputStreamReader(st.getInputStream()));// from client, InputStreamReader()將字節流轉成字符流,
//            PrintWriter output = new PrintWriter(st.getOutputStream(), true);// to client, true可以用追加的方式寫文件
            InetAddress inetAd = InetAddress.getByName("localhost");        //輸入"主機名稱"返回主機的"IP 地址"
            st = new DatagramSocket(8089, inetAd);                          //創建一個DatagramSocket實例，並將該對象綁定到指定IP地址(inetAd)、指定端口(8089)
            System.out.println("Socket at port " + st.getLocalPort());
            st.setSoTimeout(1000000);       //setSoTimeout()設超時時間，如果DatagramSocket接收不到數據就開始計時(178行)，時間跑完就拋出異常。如果計時途中讀到數據就重新計時

            while (true)
            {
                byte[] buf = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buf, buf.length);    // 创建接受类型的数据报，数据将存储在buf中，空的位置全部補"\0"
                Thread.sleep(3000);
                st.receive(packet);                                             //st接收資料，並儲存在buf裡面，如果接收不到資料就開計時(171行)
                String command = new String(buf);                               //將buf轉成string
                command = command.replace("\0", "");           //用""取代"\0"，把buf在byte[]裡面的"\0"清除
                System.out.println(command);
                if (command.equals("index") || command.startsWith("get "))
                {
                    System.out.println("Packet received from " + packet.getAddress().getHostAddress() + ":" + packet.getPort());    //getAddress返回byte型式的IP address，getHostAddress返回string型式的IP address
                    pool.submit(new Task(packet, buf));             //submit()會將一個 Callable 任務提交給 ExecutorService 並返回 Future 類型的結果(https://www.twle.cn/c/yufei/javatm/javatm-basic-executorservice.html)
                }                                                   //runnable"沒有返回值"，callable"有返回值Future類型"
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        finally
        {
            try
            {
                if (st != null)
                {
                    st.close();
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
    }
}