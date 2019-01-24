/*
One instance of this class is created for each incoming HTTP request (which maps to exactly one REST-annotated function of the class)
   - that instance is discarded at the end of the HTTP request
   - note that instance variables are therefore of no use because instances only exist for the duration of a single function call
   - any state must therefore be in "static" class variables; this matches the REST philosophy of "no per-client state at the server"
*/

import javax.ws.rs.GET;
import javax.ws.rs.Produces;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;

import javax.ws.rs.core.MediaType;

import javax.ws.rs.POST;
import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;

import com.sun.jersey.multipart.FormDataParam;
import com.sun.jersey.core.header.FormDataContentDisposition;

import javax.ws.rs.core.Context;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;

import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;

import java.io.FileWriter;
import java.io.BufferedWriter;

import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayOutputStream;

import java.io.FileOutputStream;

import java.rmi.RemoteException;
import java.rmi.registry.Registry;
import java.rmi.registry.LocateRegistry;

import java.util.*;

import java.rmi.NotBoundException;

//everything on /base/ will hit this class
@Path("")
public class Server {

    private static final String PREFIX = "/var/lib/tomcat8/webapps/myapp/files/";
    private static final String PREFIX2 = "/var/lib/tomcat8/webapps/myapp/rest/files/";

    public static byte[] convertInputStreamToByteArray(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        final int BUF_SIZE = 1024;
        byte[] buffer = new byte[BUF_SIZE];
        int bytesRead = -1;
        while ((bytesRead = in.read(buffer)) > -1) {
            out.write(buffer, 0, bytesRead);
        }
        in.close();
        byte[] byteArray = out.toByteArray();
        return byteArray;
    }

    public IChordNode aliveNode() throws RemoteException {
        boolean isAlive = false;
        while (isAlive == false)
        {
            Registry registry = LocateRegistry.getRegistry();
            String regList[] = registry.list();
            Random generator = new Random();
            int stubNum = generator.nextInt(regList.length);
            String s = "";
            try {
                s = regList[stubNum];
                IChordNode stubI = (IChordNode) registry.lookup(s);
                stubI.getKey();
                return stubI;
            }
            catch (Exception e)
            {
                try {
                    registry.unbind(s);
                }
                catch (Exception r)
                {

                }
            }

        }
        return null;
    }

    /**
     * reads file into byte array, connects to a random node and adds the task
     *
     */
    @POST
    @Path("/files/newFile")
    @Produces(MediaType.TEXT_HTML)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response newFile(@FormDataParam("name") String filename, @FormDataParam("content") InputStream contentStream, @FormDataParam("task") String taskid, @FormDataParam("content") FormDataContentDisposition fileDetail) throws IOException {
        try {
            Registry registry = LocateRegistry.getRegistry();

            String regList[] = registry.list();
            List<String> html = new ArrayList<String>();

            byte[] contentBytes = convertInputStreamToByteArray(contentStream);

            if(filename == null || filename.trim().isEmpty())
            {
                return Response.status(Response.Status.OK).entity("Key cannot be empty, please enter a key.<br><br><a href=\"http://localhost:8080/myapp/index.html\">Return to index</a><br><br><a href=\"http://localhost:8080/myapp/fileform.html\">Try again</a>").build();
            }

            if(contentStream == null)
            {
                return Response.status(Response.Status.OK).entity("You must select a file.<br><br><a href=\"http://localhost:8080/myapp/index.html\">Return to index</a><br><br><a href=\"http://localhost:8080/myapp/fileform.html\">Try again</a>").build();
            }

            /*for (int i = 0; i < regList.length; i++) {
                IChordNode stubI = (IChordNode) registry.lookup(regList[i]);
                html.clear();
                html = stubI.getTaskKey(html);
                for (int j = 0; j < html.size(); j++) {
                    if (filename.equals(html.get(j))) {
                        return Response.status(Response.Status.OK).entity("Key already taken, please select a unique key.<br><br><a href=\"http://localhost:8080/myapp/index.html\">Return to index</a><br><br><a href=\"http://localhost:8080/myapp/fileform.html\">Try again</a>").build();
                    }
                }
            }*/

            //Random generator = new Random();
            //int stubNum = generator.nextInt(registry.list().length);

            //IChordNode stubI = (IChordNode) registry.lookup(regList[stubNum]);
            aliveNode().put(filename, contentBytes, taskid, null, false, false);
            String success = "Task carried out successfully!<br><br><a href=\"http://localhost:8080/myapp/index.html\">Return to index</a><br><br><a href=\"http://localhost:8080/myapp/fileform.html\">Carry out another task</a>";

            return Response.status(Response.Status.OK).entity(success).build();
        } catch (IOException e) {
            e.printStackTrace();
        } //catch (NotBoundException nbe) {
            //nbe.printStackTrace();
        //}
        return null;
    }

    /**
     * loops through the list of tasks stored in each node and returns a list of hyperlinks (one for each completed task)
     *
     */
    @GET
    @Path("/files/filegetform")
    @Produces({MediaType.TEXT_HTML})
    public Response getTask(@PathParam("param") String key) {



        String html = "";
        try {

            Registry registry = LocateRegistry.getRegistry();

            String regList[] = registry.list();

            for (int i = 0; i < regList.length; i++) {

                IChordNode stubI = (IChordNode) registry.lookup(regList[i]);
                try {
                    stubI.getKey();
                    html = stubI.getTasks(html);
                }
                catch (Exception e) {
                }
            }

            return Response.status(Response.Status.OK).entity(html + "<br><br><a href=\"http://localhost:8080/myapp/index.html\">Return to index</a>").build();
        } catch (IOException e) {
            return Response.status(Response.Status.OK).entity("IOException").build();
        } catch (NotBoundException nbe) {
            return Response.status(Response.Status.OK).entity("NotBoundException").build();
        }

    }

    /**
     * connects to a random node and gets the result of the task associated with key 'param'
     *
     */
    @GET
    @Path("/get/{param}")
    @Produces({MediaType.TEXT_XML})
    public Response getString(@PathParam("param") String key) {

        try {
            Registry registry = LocateRegistry.getRegistry();

            Random generator = new Random();
            int stubNum = generator.nextInt(registry.list().length);
            String regList[] = registry.list();
            IChordNode stubI = (IChordNode) registry.lookup(regList[stubNum]);
            String data1 = stubI.getTaskResult(key);
            String data2 = stubI.getTaskID(key);
            String data3 = "";
            String data4 = "";
            if (data2.equals("t1"))
            {
                data3 = "<WordCount>";
                data4 = "</WordCount>";
            }
            if (data2.equals("t2"))
            {
                data3 = "<AverageLetters>";
                data4 = "</AverageLetters>";
            }
            if (data2.equals("t3"))
            {
                data3 = "<MostCommonWord>";
                data4 = "</MostCommonWord>";
            }
            if (data2.equals("t4"))
            {
                data3 = "<LongestWord>";
                data4 = "</LongestWord>";
            }
            if (data2.equals("t5"))
            {
                data3 = "<RandomWord>";
                data4 = "</RandomWord>";
            }
            return Response.status(Response.Status.OK).entity("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Task><Result>" + data3 + data1 + data4 + "</Result></Task>").build();

        } catch (IOException e) {
            e.printStackTrace();
        } catch (NotBoundException nbe) {
            nbe.printStackTrace();
        }
        return Response.status(Response.Status.OK).entity("failed").build();

    }

}
