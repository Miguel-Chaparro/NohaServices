/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package nohaservices;

import gnu.io.CommPort;
import gnu.io.CommPortIdentifier;
import gnu.io.NoSuchPortException;
import gnu.io.PortInUseException;
import gnu.io.SerialPort;
import gnu.io.UnsupportedCommOperationException;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.print.Doc;
import javax.print.DocFlavor;
import javax.print.DocPrintJob;
import javax.print.PrintException;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.SimpleDoc;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.Copies;

/**
 *
 * @author MIGUEL
 */
public class NohaServices implements Runnable {

    static final int PORT = 5656;
    static final String HTTP_200 = "HTTP/1.1 200 OK";
    static final String HTTP_417 = "HTTP/1.1 417 Expectation Failed";
    static final String HTTP_404 = "HTTP/1.1 404 Not Found";
    static final String HTTP_405 = "HTTP/1.1 405 Method Not Allowed";
    static final String HTTP_500 = "HTTP/1.1 500 Internal Server Error";
    static final String HTTP_SERVER = "Server: Java HTTP Server from Noha: 1.0";
    static final String HTTP_CONTENT = "Content-type: application/json";
    private final Socket connect;

    public NohaServices(Socket con) {
        super();
        connect = con;
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        try {
            ServerSocket serverSocket = new ServerSocket(PORT);
            while (true) {
                NohaServices server = new NohaServices(serverSocket.accept());
                Thread thread = new Thread(server);
                thread.start();
            }
        } catch (IOException e) {
            System.err.println(e);
        } catch (Exception e) {
            System.err.print(e);
        }
    }

    @Override
    public void run() {
        BufferedReader in = null;
        PrintWriter out = null;
        BufferedOutputStream dataOut = null;
        String fileRequested = null;
        try {
            in = new BufferedReader(new InputStreamReader(connect.getInputStream()));
            out = new PrintWriter(connect.getOutputStream());
            dataOut = new BufferedOutputStream(connect.getOutputStream());
            CodeValue response = new CodeValue();
            String input = in.readLine();
            String[] parse = input.split("/");
            String method = parse[0].toUpperCase().trim();
            fileRequested = parse[1];
            String[] getMethod;
            getMethod = fileRequested.split("\\?");
            if (!method.equals("GET") && !method.equals("HEAD")) {
                response.setCodigo(HTTP_405);
                response.setValor("Metodo No Soportado");
            } else {
                switch (getMethod.length) {
                    case 2:
                        response = validateMethodResource(getMethod[0].replace("/", ""), getMethod[1]);
                        break;
                    case 1:
                        response = validateMethodResource(getMethod[0].replace("/", ""), "");
                        break;
                    default:
                        response = validateMethodResource("", "");
                        break;
                }
            }
            httpHeaders(out, dataOut, response);
        } catch (IOException e) {

        } finally {
            try {
                connect.close();
            } catch (IOException e) {
                System.err.println("Error close : " + e.getMessage());
            }
        }
    }

    public void httpHeaders(PrintWriter out, BufferedOutputStream dataOut, CodeValue response) throws IOException {

        String jsonResponse;
        jsonResponse = "{\"peso\":\"" + response.getValor().trim() + "\"}";
        int longData = jsonResponse.length();
        out.println(response.getCodigo());
        out.println("Access-Control-Allow-Origin: *");
        out.println("Access-Control-Allow-Methods: POST, GET, OPTIONS");
        out.println(HTTP_SERVER);
        out.println("Date: " + new Date());
        out.println(HTTP_CONTENT);
        out.println("Content-length: " + longData);
        out.println();
        out.flush();
        dataOut.write(jsonResponse.getBytes(), 0, longData);
        dataOut.close();
        out.close();
        dataOut.flush();
    }

    private CodeValue validateMethodResource(String resource, String parameters) {
        CodeValue metodoResponse = new CodeValue();

        if (parameters == null) {
            parameters = "";
        } else {
            parameters = parameters.replace("HTTP", "").trim();
        }

        switch (resource.replace("HTTP", "").trim()) {
            case "getValueBascula":
                return getDataSerialCom(parameters);
            case "getConfigCom":
                return getConfigCom();
            case "getPrints":
                return printsList();
            case "print":
                return printerToPrint(getParamPrinters(parameters.replaceAll("%20", " ")));
            case "getValueBasculaPrueba":
                return prueba();
            default:
                metodoResponse.setCodigo(HTTP_404);
                metodoResponse.setValor("Peticion no soportada 404");
                return metodoResponse;
        }

    }

    private CodeValue prueba() {
        CodeValue response = new CodeValue();
        response.setCodigo(HTTP_200);
        response.setValor("3.980,   Kg");
        return response;
    }

    private CodeValue getDataSerialCom(String fuente) {
        CodeValue response = new CodeValue();
        atributos_bascula esquema = getParamBascula(fuente);
        SerialPort puerto_ser = null;
        int inicio = 0, fin = 0;
        InputStream in = null;
        CommPortIdentifier port = null;
        CommPort commPort = null;
        int salida = 0;
        String lectura = "";
        String lecturaTimeOut = "No fue posible obtener datos en el rango de tiempo estimado";
        String codigoTimeOut = HTTP_417;
        long start = System.currentTimeMillis();
        try {
            port = CommPortIdentifier.getPortIdentifier(esquema.getPort().trim());
            if (port.isCurrentlyOwned()) {
                response.setCodigo(HTTP_417);
                response.setValor("Puerto en uso, por favor intente mas tarde");

            } else {
                commPort = port.open(this.getClass().getName(), 2000);
                puerto_ser = (SerialPort) commPort;
                int baudRate = 9600;
                puerto_ser.setSerialPortParams(
                        baudRate,
                        SerialPort.DATABITS_8,
                        SerialPort.STOPBITS_1,
                        SerialPort.PARITY_NONE);
                in = puerto_ser.getInputStream();
                char prueba;
                while (true) {
                    salida = in.read();
                    prueba = (char) salida;
                    lectura = lectura + prueba;
                    inicio = lectura.indexOf(".");
                    fin = lectura.indexOf("Kg");
                    if (esquema.getTime() > 0) {
                        long end = start + esquema.getTime();
                        if (end <= System.currentTimeMillis()) {
                            response.setCodigo(codigoTimeOut);
                            response.setValor(lecturaTimeOut);
                            puerto_ser.close();
                            break;
                        }
                        if (inicio > fin) {
                            lectura = "";
                        } else if (inicio != -1 && fin != -1 && inicio < fin) {
                            codigoTimeOut = HTTP_200;
                            lecturaTimeOut = lectura.substring(inicio + 1, fin + 2);
                            in = puerto_ser.getInputStream();
                            lectura = "";
                        }
                    } else {
                        long end = start + 10000;
                        if (end <= System.currentTimeMillis()) {
                            response.setCodigo(codigoTimeOut);
                            response.setValor(lectura);
                            puerto_ser.close();
                            break;
                        }
                        if (inicio > fin) {
                            lectura = lectura.replace("Kg", "");
                        } else if (inicio != -1 && fin != -1 && inicio < fin) {
                            response.setCodigo(HTTP_200);
                            response.setValor(lectura.substring(inicio + 1, fin + 2));
                            puerto_ser.close();
                            break;
                        }
                    }
                }
            }
        } catch (IOException | PortInUseException | NoSuchPortException | UnsupportedCommOperationException e) {
            response.setCodigo(HTTP_500);
            response.setValor("Error al intentar conectarse al puerto: " + esquema.getPort());
        }
        //return lectura.substring(inicio + 1, fin + 2);
        return response;
    }

    private CodeValue getConfigCom() {
        CodeValue response = new CodeValue();

        Enumeration puertos_libres = null;
        CommPortIdentifier port = null;
        puertos_libres = CommPortIdentifier.getPortIdentifiers();
        String puertosLibres = "";
        int count = 0;
        while (puertos_libres.hasMoreElements()) {
            port = (CommPortIdentifier) puertos_libres.nextElement();
            count++;
            puertosLibres = puertosLibres + " " + count + ":" + port.getName();
        }
        if (puertosLibres.length() > 0) {
            response.setCodigo(HTTP_200);
            response.setValor(puertosLibres);
        } else {
            response.setCodigo(HTTP_417);
            response.setValor("No hay dispositivos Conectados");
        }
        return response;

    }

    private CodeValue printsList() {
        CodeValue response = new CodeValue();
        String listado = "";
        PrintService[] ps = PrintServiceLookup.lookupPrintServices(null, null);
        for (PrintService p : ps) {
            listado = listado + "' - '" + p.getName();
        }
        if (listado.length() > 0) {
            response.setCodigo(HTTP_200);
            response.setValor(listado);
        } else {
            response.setCodigo(HTTP_417);
            response.setValor("No hay dispositivos Conectados");
        }
        return response;
    }

    private CodeValue printerToPrint(modelPrinter Atributos) {

        CodeValue response = new CodeValue();

        PrintService[] ps = PrintServiceLookup.lookupPrintServices(null, null);
        PrintService service = null;
        InputStream is = new ByteArrayInputStream("  ".getBytes());
        for (PrintService p : ps) {
            if (Atributos.getImpresora().equals(p.getName())) {
                service = p;
                break;
            }
        }
        if (service != null) {
            PrintRequestAttributeSet pras = new HashPrintRequestAttributeSet();
            pras.add(new Copies(1));
            DocFlavor flavor = DocFlavor.INPUT_STREAM.AUTOSENSE;
            Doc doc = new SimpleDoc(is, flavor, null);
            DocPrintJob job = service.createPrintJob();
            PrintJobWatcher pjw = new PrintJobWatcher(job);
            try {
                job.print(doc, pras);
                response.setCodigo(HTTP_200);
                response.setValor("OK");

            } catch (PrintException ex) {
                Logger.getLogger(NohaServices.class.getName()).log(Level.SEVERE, null, ex);
                response.setCodigo(HTTP_417);
                response.setValor("Se presento error al Imprimir: " + ex);
            }
            pjw.waitForDone();
            try {
                is.close();
            } catch (IOException ex) {
                Logger.getLogger(NohaServices.class.getName()).log(Level.SEVERE, null, ex);
                response.setCodigo(HTTP_417);
                response.setValor("Se presento error al cerrar Documento: " + ex);
            }
        } else {
            response.setCodigo(HTTP_417);
            response.setValor("No se reconoce la impresora: " + Atributos.getImpresora());
        }

        return response;

    }

    private atributos_bascula getParamBascula(String Resource) {
        atributos_bascula atributos;
        atributos = new atributos_bascula("COM7", 0, false);
        List<CodeValue> splitParam = getParam(Resource);
        for (CodeValue codigoValor : splitParam) {
            switch (codigoValor.getCodigo()) {
                case "puerto":
                    atributos.setPort(codigoValor.getValor());
                    break;
                case "TimeOut":
                    int time;
                    try {
                        time = Integer.parseInt(codigoValor.getValor());
                    } catch (NumberFormatException e) {
                        time = 0;
                    }
                    atributos.setTime(time);
                    break;
                case "second":
                    boolean segundo = false;
                    try {
                        segundo = Boolean.parseBoolean(codigoValor.getValor());
                    } catch (Exception e) {
                        segundo = false;
                    }
                    atributos.setSecond(segundo);
                    break;
                default:
                    break;
            }
        }
        if (atributos.isSecond()) {
            atributos.setTime(atributos.getTime() * 1000);
        }
        return atributos;
    }

    private modelPrinter getParamPrinters(String Resource) {
        modelPrinter atributos;
        atributos = new modelPrinter();
        List<CodeValue> splitParam = getParam(Resource);
        for (CodeValue codigoValor : splitParam) {
            switch (codigoValor.getCodigo()) {
                case "printer":
                    atributos.setImpresora(codigoValor.getValor());
                    break;

                default:
                    break;
            }
        }

        return atributos;
    }

    private List<CodeValue> getParam(String parametros) {
        CodeValue codigoValor;
        List<CodeValue> listaCodigoValor;
        listaCodigoValor = new ArrayList();
        String[] paramPrinc;

        paramPrinc = parametros.split("&");

        for (String paramPrinc1 : paramPrinc) {
            codigoValor = new CodeValue();
            String[] cod;
            cod = paramPrinc1.split("=");
            if (cod.length > 1) {
                codigoValor.setCodigo(cod[0]);
                codigoValor.setValor(cod[1]);
                listaCodigoValor.add(codigoValor);
            }
        }
        return listaCodigoValor;
    }

}
