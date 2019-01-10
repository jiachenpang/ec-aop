package stevenTest;

import java.net.MalformedURLException;
import java.rmi.RemoteException;

import javax.xml.namespace.QName;
import javax.xml.rpc.ServiceException;

import org.apache.axis.client.Call;
import org.apache.axis.client.Service;

public class TestWebSrv {

    public static void main(String[] str) {
        String endpoint = "http://132.35.81.197:10000/aop/services/EcAopMainService?wsdl";
        String endpoint1 = "http://127.0.0.1:7001/aop/services/EcAopMainTest";
        Service service = new Service();
        String input = "world";
        String action = "hello";
        try {
            Call call = (Call) service.createCall();
            call.setTargetEndpointAddress(new java.net.URL(endpoint1));
            call.setOperationName(new QName("http://127.0.0.1:7001/aop/services/EcAopMainTest?wsdl", "hello"));
            call.setOperation(action);
            String xml = (String) call.invoke(new Object[] { input });
            System.out.println(xml);
        }
        catch (RemoteException e) {
            e.printStackTrace();
        }
        catch (MalformedURLException e) {
            e.printStackTrace();
        }
        catch (ServiceException e) {
            e.printStackTrace();
        }
    }
}
