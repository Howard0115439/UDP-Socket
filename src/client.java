import java.io.*;
import java.net.*;

//DatagramPacket用來存放數據，DatagramSocket用來接受或發送資料
public class client
{
    public static void main(String[] args)
    {
        DatagramSocket st = null;
        try
        {
            byte[] buf = new byte[1024];
            InetAddress inetAd = InetAddress.getByName("localhost");                        //輸入"主機名稱"返回主機的"IP 地址"
            st = new DatagramSocket(8088, inetAd);                                          //創建一個DatagramSocket實例，並將該對象綁定到指定IP地址(inetAd)、指定端口(8088)，因為8089被server占用
            System.out.println("Socket at port " + st.getLocalPort());
            st.setSoTimeout(1000000);                                                           //setSoTimeout()設超時時間，如果DatagramSocket接收不到數據就開始計時(42行)，時間跑完就拋出異常。如果計時途中讀到數據就重新計時
            DatagramPacket packet = new DatagramPacket(buf, 1024, inetAd, 8089);    //DatagramPacket用來存放數據，DatagramSocket用來接受或發送資料，port:8089表示server端存放的數據
//            Socket st = new Socket("localhost", 8089);
//            PrintWriter output = new PrintWriter(st.getOutputStream(), true);// to server
//            BufferedReader input = new BufferedReader(new InputStreamReader(st.getInputStream()));// from server
            BufferedReader type_in = new BufferedReader(new InputStreamReader(System.in));// from keyboard
            while (true)
            {
                System.out.print("client input : ");
                String command = type_in.readLine();                            //輸入字串
                if (command.equals("index") || command.startsWith("get "))      //startsWith()表示開始的值，如果正確回傳true
                {
//                    output.println(command);
//                    String firstLine = "";
//                    String line = "";
//                    while (!(line = input.readLine()).equals("end"))           //如果還沒readLine()讀到最後(server端用EOF表示最後元素)
//                    {
//                        if (firstLine.equals(""))           //剛開始firstLine為空
//                        {
//                            firstLine = line;
//                        }
//                        System.out.println(line);
//                    }
                    packet.setData(command.getBytes(), 0, command.getBytes().length);       //將command轉成byte[]並傳送到server請求資料
                    st.send(packet);
                    packet.setData(buf, 0, buf.length);                                     //接收來自server.java(39~126行)的信息並存入buf，ex.index列出所有資訊(server.java-52行)，get test1.txt接收到txt檔案的每一行(server.java-79行)
                    st.receive(packet);
                    int packetCount = 1;
                    int reciveSize = 0;
                    StringBuilder sb = new StringBuilder();
                    if (command.equals("index"))
                    {
                        String reply = new String(buf);
                        reply = reply.replace("\0", "");
                        sb.append(reply);
                    }
                    else        //command = "get"
                    {
                        while ((reciveSize = packet.getLength()) != 0)                                              //****讀取server傳過來的txt檔裡面的字串*****
                        {
                            // if a is exit sign is recived
                            String reply = new String(buf);                                                         //將txt檔裡面每一行資料轉成string
                            reply = reply.replace("\0", "");
                            if (reply.equals("exit"))
                            {
                                System.out.println("End");
                                packet.setData("exit".getBytes(), 0, "exit".getBytes().length);
                                st.send(packet);
                                break;
                            }
                            sb.append(reply);
                            packet.setData("successful".getBytes(), 0, "successful".getBytes().length);                     //接收到資料就回傳"successful"信息
                            st.send(packet);
                            buf = new byte[1024];                                                                       //清除buf資料
                            packet.setData(buf, 0, buf.length);                                                 //如果傳送成功，就繼續接收txt檔下一行的資料(和client.java-41行相同)，如果傳送失敗就重新接收資料txt檔裡面的同一行資料(server.java-92~96行)
                            packetCount++;
                            st.receive(packet);
                        }
                    }
                    sb.append("\n");
                    String res = sb.toString();
                    System.out.print(res);              //印出txt檔裡面的每一行信息

                    if (command.startsWith("get ") && !res.startsWith("error"))       //如果輸入是get且檔案存在的話(server-66行)，就break
                    {
                        break;
                    }
                }
                else
                {
                    System.err.println("Invalid command.");
                }
            }
        }
        catch (ConnectException e)
        {
            System.err.println("Timeout.");
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
                    st.close();
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
    }
}