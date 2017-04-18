/*
 *  Copyright (c) 2013-2017 Yale University. All rights reserved.
 *
 *  THIS SOFTWARE IS PROVIDED "AS IS," AND ANY EXPRESS OR IMPLIED
 *  WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 *  MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE, ARE EXPRESSLY
 *  DISCLAIMED. IN NO EVENT SHALL YALE UNIVERSITY OR ITS EMPLOYEES BE
 *  LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 *  CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED, THE COSTS OF
 *  PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA OR
 *  PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 *  LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 *  NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 *  SOFTWARE, EVEN IF ADVISED IN ADVANCE OF THE POSSIBILITY OF SUCH
 *  DAMAGE.
 *
 *  Redistribution and use of this software in source or binary forms,
 *  with or without modification, are permitted, provided that the
 *  following conditions are met:
 *
 *  1. Any redistribution must include the above copyright notice and
 *  disclaimer and this list of conditions in any related documentation
 *  and, if feasible, in the redistributed software.
 *
 *  2. Any redistribution must include the acknowledgment, "This product
 *  includes software developed by Yale University," in any related
 *  documentation and, if feasible, in the redistributed software.
 *
 *  3. The names "Yale" and "Yale University" must not be used to endorse
 *  or promote products derived from this software.
 */
/* 
 * Author  Yue Ji 
 * Created on March 5, 2014, 9:52 AM
 * Retrieve bib marc by bibid.
 * Updated on March 8, 2016 3:20 PM. Change result set release from rs.close() to rs=null. 
 * BibMARC.java
 */
package src;

//import static Precompile.InitPrecompile.con;
import java.io.*;
import java.net.*;
import javax.servlet.*;
import javax.servlet.http.*;
import java.sql.*;
import java.util.*;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.util.regex.*; 
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.sql.DataSource;
import org.marc4j.MarcReader;
import org.marc4j.MarcStreamReader;
import org.marc4j.MarcXmlWriter;
import org.marc4j.MarcWriter;
import org.marc4j.marc.Record;

public class GetBibMARC extends HttpServlet {
    private ServletConfig config = null;
                   
/** Initialize global variables */
    public void init(ServletConfig servletConfig) throws ServletException {
        super.init(servletConfig);
        config = servletConfig;
    }

/** Processes requests for both HTTP <code>GET</code> and <code>POST</code> methods.
     * @param request servlet request
     * @param response servlet response
*/
    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
// Connect to socket server.        
        int portNumber = 7010; // CAT port
        String moduleName = "CAT"; // CAT module
        DataOutputStream outputToServer = null;
        DataInputStream inputFromServer = null;
        Socket socketToServer = null;
        
        BufferedReader bd = null;
        Reader sReader = null;
        StringBuffer buffer = new StringBuffer();
        int currChar = 1, preChar = 1;
        StringTokenizer retToken = null;
        boolean goodReturn = false;
        MarcReader reader = null;
        MarcWriter writer = null;  
        Record record = null;    
        PrintWriter out = null;
        Writer xmlPrinter = null;
        InputStream input = null, ips = null;
        InputStreamReader ipsr = null;
        OutputStream output = null;
        String fileSeparator = System.getProperty("file.separator");
      //  SimpleDateFormat sdf = new SimpleDateFormat("MMddyy.HHmmss");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmssSSS");
        String dateString = sdf.format(new Date());
        PreparedStatement pstmtOMR = null, pstmtISBN = null, pstmtISSN = null;
        ResultSet rsOMR = null, rsISBN = null, rsISSN = null;   
        Connection con = null;
        boolean available = false;
        Pattern p = null;
        Matcher m = null;
        Map parmMap = request.getParameterMap();
        String fromUser = "", fromServerString = "", br = "", retData = "", tempName = "", line = "";
        String  bibid = "", isxn = "", oclcmrn = "";
        String sqlOMR = "", sqlISBN = "", sqlISSN = "";
        
        try {
/*   System.out.println(System.getProperty("catalina.base"));
     catalina.home: C:\Program Files\netbeans-5.5.1\enterprise3\apache-tomcat-5.5.17 
     catalina.base: C:\Users\yj33\.netbeans\5.5.1\apache-tomcat-5.5.17_base
*/          
//   Statement stmt = Precompile.InitPrecompile.con.createStatement();
//   rs = stmt.executeQuery("SELECT * from patron where patron_id=153015");
           
            con = Precompile.InitPrecompile.ds.getConnection();
            response.setContentType("text/xml;charset=UTF-8");
            response.setHeader("Cache-Control", "nocache");
            request.setCharacterEncoding("UTF-8"); 
            response.setCharacterEncoding("UTF-8"); 
            out = response.getWriter();
            
            if (parmMap.containsKey("bibid")) {
                bibid = request.getParameter("bibid").trim();   
// Match digits only.
                if (bibid.matches("^[0-9]+$"))   
                   available = true; 
            }  
                 
            if (parmMap.containsKey("isxn")) 
                isxn = request.getParameter("isxn").trim().toUpperCase();   
              
            if (parmMap.containsKey("oclcmrn")) {
                oclcmrn = request.getParameter("oclcmrn").trim();  
// Extract digits piece.                   
                p = Pattern.compile("[0-9]+");
                m = p.matcher(oclcmrn);
                if (m.find())                   
                   oclcmrn = m.group();                
            }

// Find bibid by searching OCLC MR number. e.g. display_heading='ocm00000030'
            sqlOMR = "select * from bib_index"
                   + " where index_code = '079A'"
                   + " and normal_heading = ?";
//+ " and normal_heading='00000030'";
            pstmtOMR = con.prepareStatement(sqlOMR); 
               
// Find bibid by searching ISBN e.g. 3327000409, 0670119911
            sqlISBN = "select * from bib_index"
                    + " where index_code='020A'"
                    + " and upper(display_heading) = ?";
            pstmtISBN = con.prepareStatement(sqlISBN);  
        
// Find bibid by searching ISSN e.g. 0002-094X
            sqlISSN = "select * from bib_index"
                    + " where index_code='022A'"
                    + " and upper(display_heading) = ?";
            pstmtISSN = con.prepareStatement(sqlISSN);  
           
            if (bibid == "") {
// Get bibid by searching OCLC MR number.
              if (oclcmrn != "") {
                pstmtOMR.setString(1,oclcmrn);               
                rsOMR = pstmtOMR.executeQuery();         
                if (rsOMR.next()) {
                    bibid = rsOMR.getString("bib_id");
                    available = true;
                } 
              }
             
// Get bibid by searching ISBN.  
              if (!available) {
                if (isxn != "") {
                    pstmtISBN.setString(1,isxn);               
                    rsISBN = pstmtISBN.executeQuery();         
                    if (rsISBN.next()) {
                        bibid = rsISBN.getString("bib_id");
                        available = true;
                    } 
                }
              }
             
// Get bibid by searching ISSN. 
              if (!available) {
                if (isxn != "") {
                    pstmtISSN.setString(1,isxn);               
                    rsISSN = pstmtISSN.executeQuery();         
                    if (rsISSN.next()) {
                        bibid = rsISSN.getString("bib_id").trim();
                        available = true;
                    } 
                }
              }
            } 
           
//  The INIT process of conversation between client and server using voyager API.
//  Send INIT request, get back INIT data. Send header and tags for INIT.          
     /*   Precompile.InitPrecompile.socketToServer = new Socket(Precompile.InitPrecompile.initProp.getProperty("Server_IP_address"),portNumber);
          Precompile.InitPrecompile.outputToServer = new DataOutputStream(Precompile.InitPrecompile.socketToServer.getOutputStream());
          Precompile.InitPrecompile.inputFromServer = new DataInputStream(Precompile.InitPrecompile.socketToServer.getInputStream());
     */
            socketToServer = new Socket(Precompile.InitPrecompile.initProp.getProperty("Server_IP_address"),portNumber);
            outputToServer = new DataOutputStream(socketToServer.getOutputStream());
            inputFromServer = new DataInputStream(socketToServer.getInputStream());
          
            fromUser = "";
            fromUser = "[HEADER]"+'\0'+"CO=EISI"+'\0'+"AP="+moduleName+'\0'
                     +"VN=1.03"+'\0'+"TO=60"+'\0'+"RQ=INIT"+'\0'
                     +"[DATA]"+'\0'+"AP="+moduleName+'\0'
                     +"VN="+Precompile.InitPrecompile.initProp.getProperty("Voyager_version_number")+'\0'+'\0';
            outputToServer.write(fromUser.getBytes());
            sReader = new BufferedReader(new InputStreamReader(inputFromServer, "UTF-8"));
            buffer = null;
            buffer = new StringBuffer();                                 
            while (true) {
                currChar = sReader.read();
                if ((preChar == 0) && (currChar == 0))
                    break;
                buffer.append((char)currChar);
                preChar = currChar;
            }
            fromServerString = buffer.toString();      
            retToken = new StringTokenizer(fromServerString, "\0");
            goodReturn = false;         
            while (retToken.hasMoreTokens()) {
                retData = retToken.nextToken(); 
                if (retData.toUpperCase().equalsIgnoreCase("RC=0"))  {
                    goodReturn = true;
                    break;
                }
            }            
            if (!goodReturn) {
                System.err.println("Cannot INITIALIZE!");
                System.exit(1);
            }       
          
//    bibid="3941";
//    bibid="616193"; // a bunch of item records.
// Get BIB marc record.            
            fromUser = "";
            fromUser = "[HEADER]"+'\0'+"CO=EISI"+'\0'+"AP="+moduleName+'\0'+"VN=1.03"
                     +'\0'+"TO=60"+'\0'+"RQ=BIB_RETRIEVE"+'\0'+"[DATA]"+'\0'
                     +"BI="+bibid+'\0'+'\0';                
            outputToServer.write(fromUser.getBytes());
            sReader = new BufferedReader(new InputStreamReader(inputFromServer, "UTF-8"));
            buffer = null;
            buffer = new StringBuffer();
            while (true) {
                currChar = sReader.read();
                if ((preChar == 0) && (currChar == 0))
                    break;
                    buffer.append((char)currChar);
                    preChar = currChar;
                }
                fromServerString = buffer.toString();      
                retToken = new StringTokenizer(fromServerString, "\0");
                goodReturn = false;
         
                while (retToken.hasMoreTokens()) {
                   retData = retToken.nextToken(); 
                   if (retData.toUpperCase().equalsIgnoreCase("RC=0"))  {
                       goodReturn = true;
                   }
                   else if (retData.toUpperCase().indexOf("RC=") == 0)  {
/* Write into log file.
                        totalLog++;
                        errLogPrinter.println(bibid + " Either BIB record doesn't exist in Voyager, or BIB record has the problem."); 
*/
                        break; 
                   }
                   if (retData.indexOf("BR=") >= 0) 
                        br = retData.substring(retData.indexOf("BR=")+3,retData.length());   
                   }
// System.out.println("br: " + br);                    
// if (!goodReturn)  continue;
                       
                   tempName = getServletContext().getRealPath("/") + "WEB-INF" + fileSeparator
                            + "output" + fileSeparator + "tempxml_" + dateString + ".xml";   
              
// tempName = "c:\\temp\\temp.xml";
                   input = new ByteArrayInputStream(br.getBytes("UTF-8"));
                   output = new FileOutputStream(tempName);
                   reader = new MarcStreamReader(input, "UTF8");
                   writer = new MarcXmlWriter(output,"UTF8");                    
//    writer = new MarcXmlWriter(System.out,true);
    
                   while (reader.hasNext()) {
                        record = reader.next();            
// System.out.println("Record: " + record.toString());
                        writer.write(record);            
                   }
                   writer.close(); 
                   line = "";
                   ips = new FileInputStream(tempName); 
                   ipsr = new InputStreamReader(ips,"UTF8");
                   bd = new BufferedReader(ipsr);
            
                   line = bd.readLine();
                   out.println(line);
      } catch (SQLException sqle) {
            System.err.println("SQL error in VAPI GetBibMARC: " + sqle.getMessage());         
    } catch (Throwable anything) {
        System.err.println("Abnormal exception caught in VAPI GetBibMARC: " + anything);
    }
        finally {
          try {
            out.flush();
            out.close();
            output.flush();
            output.close();
            bd.close();
            new File(tempName).delete();
            rsOMR = null;
            rsISBN = null;
            rsISSN = null;
            pstmtOMR.close();
            pstmtISBN.close(); 
            pstmtISSN.close();
            outputToServer.close();
            inputFromServer.close();
            socketToServer.close();   
            con.close(); 
         } catch (SQLException sqle) {
            System.err.println(sqle.getMessage());
         } 
        }
  }
    
/** Handles the HTTP <code>GET</code> method.
     * @param request servlet request
     * @param response servlet response
*/
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, java.io.IOException {
        processRequest(request, response);
    }
    
/** Handles the HTTP <code>POST</code> method.
     * @param request servlet request
     * @param response servlet response
*/
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, java.io.IOException {
        processRequest(request, response);
    }
    
    public String replaceAllChars(String original, char find, String replacement) {
        String result = "";
        int i = 0;
        for (i = 0; i < original.length(); i++) {
            if (original.charAt(i) == find) {
                result = result + replacement;
            }
            else {
                result = result + original.charAt(i);
            }
        }
        return result;
    }
    
       
// Convert xml entities.
 public String xmlEntities(String s) {   
    StringBuffer sb = new StringBuffer("");

/*Also checks that no entities are converted twice by removing any already in the input string.   
  input = "Replace a with bbb in Java"; 
  output = input.replaceAll("a", "bbb");
  output = "Replbbbce bbb with bbb in Jbbbvbbb"; 
*/
    try {
        String sClean = new String(s);
    /*
    sClean = sClean.replaceAll("&amp;", "&");
    sClean = sClean.replaceAll("&quot;", "\"");
    sClean = sClean.replaceAll("&lt;", "<");
    sClean = sClean.replaceAll("&gt;", ">");
 */      
        for (int i = 0; i < sClean.length(); i++) {
            char c = sClean.charAt(i);
            switch(c) {
                case '&':
                    sb.append("&amp;");
                    break;
// apos is not html's entity. It is xml's entity.
                case '\'':
                    sb.append("&apos;");
                    break;
                case '"':
                    sb.append("&quot;");
                    break;
                case '<':
                    sb.append("&lt;");
                    break;
                case '>':
                    sb.append("&gt;");
                    break;
//Carriage Return is '0D' in hex and '13' in decimal. This moves the cursor to the beginning of same line. 
                case 0x0D: 
                    sb.append("&#13;");
                    break;
// Line Feed is '0A' in hex and '10' indecimal. This moves the cursor to the next line at the same position. 
                case 0x0A:
                    sb.append("&#10;");
                    break;
                case 0x1E:
                case 0x1F:
                case 0x1D:
                    sb.append(' ');
                    break;
                default:
                    sb.append(c);
            }
        }
    } catch(Exception e) { 
      System.err.println("xmlEntities function happened error. " + e.getMessage());            
   }
   return sb.toString();      
 }
}