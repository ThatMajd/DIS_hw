import java.io.*;
import java.net.*;
import java.util.*;

public class Node {

    private Integer num_of_nodes;
    private Map<Integer, List<Integer>> routingTable;
    public Integer id;
    private Map<Integer, Double> neighbors;
    private Map<Integer, List<Integer>> ports;
    // ports is of the form (neighbor_id) -> (send_port, listen_port)
    private Map<Integer, Integer> seqCounter;
    private Map<Integer, String> msgs;


    // TODO
    // each node should have a Routing table, that has an entry for all the vertices

    public static <K, V> void printMap(Map<K, V> map) {
        for (Map.Entry<K, V> entry : map.entrySet()) {
            System.out.println(entry.getKey() + " : " + entry.getValue());
        }
    }
    public Node(String line, Integer num_of_nodes){
        this.ports = new HashMap<>();
        this.neighbors = new HashMap<>();
        this.seqCounter = new HashMap<>();
        this.msgs = new HashMap<>();
        this.num_of_nodes = num_of_nodes;
        parseLine(line);
    }

    public void update_weight(Integer neighbor, Double new_weight){
        this.neighbors.put(neighbor, new_weight);
    }
    public void send() throws IOException {
        /*
        Testing function to check whether the communication works
         */
        String msg = "from" + this.id;
        flood(this.id, msg, 0);
    }

    public void handleMsg(Integer source, String msg){
        /*
        TODO:
        This function handles incoming data coming from other nodes in order to construct
        a local copy of the entire graph
         */
        this.msgs.put(source, msg);
    }
    public void read_msgs(){
        System.out.println(this.id + " is reading: ");
        for (String msg : this.msgs.values()){
            System.out.println(msg);
        }
    }
    private int flood(Integer source, String msg, Integer seq) throws IOException {
        if (this.msgs.size() == this.num_of_nodes){
            return 1;
        }
        handleMsg(source, msg);
        seq++; // incrementing the seq number of the msg for a new broadcast
        String final_msg = source+"/"+msg+"/"+seq;
        for (Integer neighbor: this.neighbors.keySet()){
            sendMessage(final_msg, neighbor);
        }
        return 0;
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
    private void sendMessage(String msg, Integer receiver) throws IOException {
        /*
        This function sends a message to another node
        @param msg that message to be sent
        @param receiver the id of the receiver
        @returns None
         */
        try {
            int port = this.ports.get(receiver).get(0);
            System.out.println(this.id + " sending on " + port);
            Socket socket = new Socket(InetAddress.getByName("localhost"), port);
            OutputStream out = socket.getOutputStream();
            out.write(msg.getBytes());
            socket.close();
        } catch (IOException e){
            e.printStackTrace();
        }
    }

    public void receiveMessages() throws IOException{
        /*
        Listens on all the neighbor's listening ports
         */
        for (Integer neighbor: this.neighbors.keySet()){
            Thread thread = new Thread(() -> {
                int port = this.ports.get(neighbor).get(1);
                try {
                    System.out.println(this.id + " listening on " + port);
                    ServerSocket serverSocket = new ServerSocket(port);
                    while (true) {
                        Socket socket = serverSocket.accept();
                        InputStream in = socket.getInputStream();
                        byte[] message = new byte[1024];
                        in.read(message);
                        String msg = new String(message);

                        String[] parts = msg.trim().split("/");

                        int source = Integer.parseInt(parts[0]);
                        String orig_msg = parts[1];
                        int seq = Integer.parseInt(parts[2]);

                        if (!this.seqCounter.containsKey(source)){
                            this.seqCounter.put(source, 0);
                        }
                        if (seq > this.seqCounter.get(source)) {
                            this.seqCounter.put(source, seq);
                            if (flood(source, orig_msg, seq) == 1){
                                socket.close();
                            }
                        }

                        socket.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            thread.start();
        }
    }
}