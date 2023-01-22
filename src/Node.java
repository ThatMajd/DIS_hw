import java.io.*;
import java.net.*;
import java.util.*;

public class Node extends Thread{

    private Integer num_of_nodes;
    public Integer id;
    private Map<Integer, Double> neighbors;
    private Map<Integer, List<Integer>> ports;
    // ports is of the form (neighbor_id) -> (send_port, listen_port)
    private Map<Integer, Integer> seqCounter;
    private Map<Integer, String> msgs;
    private Boolean stop_listening;
    private Map<Integer, Socket> sendingSockets;
    private List<Integer> curr_listening;

    public ExManager manager;


    // TODO
    // each node should have a Routing table, that has an entry for all the vertices

    public static <K, V> void printMap(Map<K, V> map) {
        for (Map.Entry<K, V> entry : map.entrySet()) {
            System.out.println(entry.getKey() + " : " + entry.getValue());
        }
    }
    public Node(String line, Integer num_of_nodes, ExManager m){
        this.ports = new HashMap<>();
        this.neighbors = new HashMap<>();
        this.seqCounter = new HashMap<>();
        this.msgs = new HashMap<>();
        this.num_of_nodes = num_of_nodes;
        this.stop_listening = false;
        this.curr_listening = new ArrayList<>();
        this.sendingSockets = new HashMap<>();

        this.manager = m;

        parseLine(line);
        receiveMessages();
    }

    public int get_num_neighs(){
        return this.neighbors.size();
    }

    @Override
    public void run() {
        assert false;
        super.run();
        send();
    }
    public int num_msgs(){
        return this.msgs.size();
    }
    private void send(){
        String msg = "from" + this.id;
        handleMessage(this.id+"/"+msg+"@");
    }
    public void read_msgs(){
        assert !is_listening();
        System.out.println(this.id + " " + (this.msgs.size() == this.num_of_nodes));
    }

    public void update_weight(Integer neighbor, Double new_weight){
        this.neighbors.put(neighbor, new_weight);
    }
    private void parseLine(String line){
        String[] parts = line.split(" ");
        this.id = Integer.parseInt(parts[0]);
        for (int i = 1; i < parts.length; i += 4) {
            int neighbor = Integer.parseInt(parts[i]);
            double weight = Double.parseDouble(parts[i + 1]);
            int send_port = Integer.parseInt(parts[i + 2]);
            int listen_port = Integer.parseInt(parts[i + 3]);

            if (!neighbors.containsKey(neighbor)) {
                neighbors.put(neighbor, weight);
                ports.put(neighbor, new ArrayList<>());
            }
            ports.get(neighbor).add(send_port);
            ports.get(neighbor).add(listen_port);
        }
    }


    private synchronized void handleMessage(String msg){
        // msg is of the form (msg, source)
        if (msg.equals("")){
            return;
        }
        for (String s: msg.split("@")) {
            String[] parts = s.split("/");
            Integer source = Integer.parseInt(parts[0]);
            String org_msg = parts[1];
            //System.out.println(this.id + "-" + s);
            if (!this.msgs.containsKey(source)) {
                this.msgs.put(source, org_msg);
                for (Integer neighbor : this.neighbors.keySet()) {
                    // send msg
                    sendMessage(s+"@", neighbor);
                }
            }
        }
    }
    public void killListeningSockets(){
        for (Socket s: this.sendingSockets.values()){
            try{
                s.close();
            } catch (IOException e){
                e.printStackTrace();
            }
        }
    }
    private void sendMessage(String msg, Integer receiver){
        if (!this.sendingSockets.containsKey(receiver)){
            try {
                int send_port = this.ports.get(receiver).get(0);
                Socket client = new Socket(InetAddress.getByName("localhost"), send_port);
                this.sendingSockets.put(receiver, client);
            }
            catch (IOException e){
                e.printStackTrace();
            }
        }
        while (!stop_listening) {
            try {
                Socket client = this.sendingSockets.get(receiver);
                OutputStream out = client.getOutputStream();
                out.write((msg).getBytes());
                out.flush();
                break;
            } catch (ConnectException e) {
                // TODO;
                // BUG - There are messages waiting to be sent when we stop
                //       listening on other nodes, which leads to error
                System.out.println("Problem with host/port or host is busy");
                e.printStackTrace();
                try {
                    sleep(2500);
                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }
    public Boolean is_listening(){
        return this.curr_listening.size() == this.neighbors.size();
    }
    private synchronized void incrementListening(int port){
        if (!this.curr_listening.contains(port)) {
            this.curr_listening.add(port);
        }
    }
    private synchronized void decrementListening(int port){
        this.curr_listening.remove(Integer.valueOf(port));
    }
    public void stop_receiving(){
        this.stop_listening = true;
    }

    public void receiveMessages(){
        for (Integer neighbor: this.neighbors.keySet()){
                Thread thread = new Thread(() -> {
                    try {
                        //System.out.println("Node "+this.id+" is listening on port " + listen_port);
                        ServerSocket serverSocket = new ServerSocket(this.ports.get(neighbor).get(1));
                        serverSocket.setSoTimeout(5000);
                        Socket socket = null;
                        while (!this.stop_listening){
                            try {
                                incrementListening(this.ports.get(neighbor).get(1));
                                if (socket == null){
                                    socket = serverSocket.accept();
                                }
                                InputStream in = socket.getInputStream();
                                byte[] msg_bits = new byte[1024];
                                in.read(msg_bits);
                                String msg = new String(msg_bits);
                                handleMessage(msg.trim());

                            } catch (SocketTimeoutException e){
                                System.out.println("JERE");
                                decrementListening(this.ports.get(neighbor).get(1));
                            }
                        }
                        if (socket == null){
                            System.out.println("Something fishy");
                        }
                        socket.close();
                        serverSocket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
                thread.start();
            }
    }

}