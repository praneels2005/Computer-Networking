import java.io.*;
import java.net.*;
import java.util.*;
import java.nio.*;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
//import javax.servlet.http.HttpServletResponse;

public final class WebServer 
{
    public static void main(String[]args) throws Exception
    {
        //Setting port number that provides regular HTTP service
        int port = 8888;
        int port2 = 5555;
        
        //Two ServerSocketChannel instances, each bounded to a different port
        ServerSocketChannel ssc = ServerSocketChannel.open(); 
        ssc.socket().bind(new InetSocketAddress(port));
        ssc.configureBlocking(false);
        

        ServerSocketChannel ssc2 = ServerSocketChannel.open();
        ssc2.socket().bind(new InetSocketAddress(port2));
        ssc2.configureBlocking(false);
        
        //Get the selector
        Selector selector = Selector.open();
        ssc.register(selector,SelectionKey.OP_ACCEPT);
        ssc2.register(selector,SelectionKey.OP_ACCEPT);

        //Process HTTP service requests in an infinite loop
        while(true)
        {
            try
            {
                //available channels for I/O
                selector.select();
                Set <SelectionKey> keys = selector.selectedKeys();
                //Iterate through channels
                for(Iterator<SelectionKey> itr = keys.iterator(); itr.hasNext();)
                {
                    SelectionKey key = itr.next();

                    //Identifying SocketChannel and it's origin server
                    ServerSocketChannel c = (ServerSocketChannel) key.channel();
                    if(key.isAcceptable() && c == ssc)
                    {
                        //Isolating socket from channel
                        Socket connectionSocket = c.accept().socket();
                        Thread thread = new Thread(new HttpRequest(connectionSocket));
                        thread.start();
                        
                    }
                    else if(key.isAcceptable() && c == ssc2)
                    {
                        Socket connectionSocket = c.accept().socket();
                        Thread thread = new Thread(new MovedRequest(connectionSocket));
                        thread.start();
                    }
                    
                    itr.remove();
                }    
            }
            catch(Exception e)
            {
                System.out.println(e);
            }
        }

    }
}

// Passes to Thread's constructor an instance of some class that implements the
// Runnable interface
final class HttpRequest implements Runnable {
    // CR(Carriage Return): Moves the cursor to the beginning of the line without
    // advancing to the next line
    // LF(Line Feed): Moves the cursor down to the next line.

    final static String CRLF = "\r\n";
    Socket socket;

    // Constructor to store a reference to the connection socket
    public HttpRequest(Socket socket) throws Exception {

        this.socket = socket;

    }

    // Implement the run() method the Runnable interface
    public void run() {
        try {
            processRequest();
        } catch (Exception e) {
            // Signaling an error or an unexpected condition during the execution of a program
            System.out.println(e);
        }

    }

    private static void sendBytes(FileInputStream fis, OutputStream os) throws Exception {
        // Construct a 1K buffer to hold bytes on their way to the socket.
        byte[] buffer = new byte[1024];
        int bytes = 0;

        // Copy requested file into the socket's ouptut stream.
        while ((bytes = fis.read(buffer)) != -1) {
            os.write(buffer, 0, bytes);
        }
    }

    private static String contentType(String fileName) {
        if (fileName.endsWith(".htm") || fileName.endsWith(".html")) {
            return "text/html";
        }
        if (fileName.endsWith(".gif")) {
            return "image/gif";
        }
        if (fileName.endsWith(".jpeg")) {
            return "image/jpeg";
        }

        return "image/jpeg";
    }

    private void processRequest() throws Exception {

        // Get a reference to the socket's input and output streams
        InputStream is = socket.getInputStream();
        DataOutputStream os = new DataOutputStream(socket.getOutputStream());

        // Set up input stream filters
        BufferedReader br = new BufferedReader(new InputStreamReader(is));

        // Get the request line of the HTTP request message
        // First item available in the input stream will be the HTTP request line
        String requestLine = br.readLine();

        // Display the requestLine
        System.out.println();
        System.out.println(requestLine);

        // Get and display the header lines
        String headerLine = "";
        // We are able to set headerLine to connection socket's headerlines
        while ((headerLine = br.readLine()).length() != 0) {
            System.out.println(headerLine);
        }

        // Extract the filename from the request line.
        StringTokenizer tokens = new StringTokenizer(requestLine);
        tokens.nextToken(); // skip over the method, which should be "GET"
        String fileName = tokens.nextToken();

        // Prepend a "." so that file request is within the current directory.
        fileName = "." + fileName;

        // Open the requested file.
        FileInputStream fis = null;
        boolean fileExists = true;

        try {
            fis = new FileInputStream(fileName);
        } catch (FileNotFoundException e) {
            fileExists = false;
        }

        // Construct the response message
        String statusLine = null;
        String contentTypeLine = null;
        String entityBody = null;
        if (fileExists) {
            statusLine = "HTTP/1.1 200 OK" + CRLF;
            contentTypeLine = "Content-type: " + contentType(fileName) + CRLF;
        } else {
            statusLine = "HTTP/1.1 404 NOT FOUND" + CRLF;
            contentTypeLine = "Content-Type: " + contentType(fileName) + CRLF;
            entityBody = "<HTML>" +
                    "<HEAD><TITLE>Not Found</TITLE></HEAD>" +
                    "<BODY>Not Found</BODY></HTML>";
        }

        // Send the status line.
        os.writeBytes(statusLine);
        // Send the content type line.
        os.writeBytes(contentTypeLine);

        // Send a blank line to indicate the end of the header
        os.writeBytes(CRLF);

        // Send the entity body.
        if (fileExists) {
            sendBytes(fis, os);
            fis.close();
        } else {
            os.writeBytes(entityBody);
        }

        // Close streams and socket
        os.close();
        br.close();
        socket.close();

    }
}

final class MovedRequest implements Runnable {
    final static String CRLF = "\r\n";
    Socket socket;

    // Constructor to store a reference to the connection socket
    public MovedRequest(Socket socket) throws Exception {

        this.socket = socket;

    }

    // Implement the run() method the Runnable interface
    public void run() {
        try {
            processRequest();
        } catch (Exception e) {
            // Signaling an error or an unexpected condition during the execution of a program
            System.out.println(e);
        }

    }

    private void processRequest() throws Exception {

        DataOutputStream os = new DataOutputStream(socket.getOutputStream());

        // Construct the response message
        String statusLine = null;
        String entityBody = null;


        statusLine = "HTTP/1.1 301 Moved Permanently" + CRLF;
        entityBody = "Location: http://www.google.com" + CRLF;
        

        os.writeBytes(statusLine);
        
        os.writeBytes(entityBody);

        // Close output stream and socket
        os.close();
        socket.close();

    }

}