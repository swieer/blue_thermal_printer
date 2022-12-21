package id.kakzaki.blue_thermal_printer;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.ServiceConnection;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.os.IBinder;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.EventChannel.StreamHandler;
import io.flutter.plugin.common.EventChannel.EventSink;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.RequestPermissionsResultListener;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import net.posprinter.posprinterface.IMyBinder;
import net.posprinter.posprinterface.TaskCallback;
import net.posprinter.service.PosprinterService;
import net.posprinter.utils.PosPrinterDev;
import net.posprinter.utils.DataForSendToPrinterTSC;
import net.posprinter.posprinterface.ProcessData;


public class BlueThermalPrinterPlugin implements FlutterPlugin, ActivityAware,MethodCallHandler, RequestPermissionsResultListener {

  private static final String TAG = "BThermalPrinterPlugin";
  private static final String NAMESPACE = "blue_thermal_printer";
  private static final int REQUEST_COARSE_LOCATION_PERMISSIONS = 1451;
  private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
  private static ConnectedThread THREAD = null;
  private BluetoothAdapter mBluetoothAdapter;

  private Result pendingResult;

  private EventSink readSink;
  private EventSink statusSink;

  private FlutterPluginBinding pluginBinding;
  private ActivityPluginBinding activityBinding;
  private final Object initializationLock = new Object();
  private Context context;
  private MethodChannel channel;

  private EventChannel stateChannel;
  private BluetoothManager mBluetoothManager;

  private Activity activity;
  private static IMyBinder myBinder;
  private static boolean IS_CONNECT = false;

  public BlueThermalPrinterPlugin() {
  }

  ServiceConnection mSerconnection = new ServiceConnection() {
    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
      myBinder = (IMyBinder) service;
      Log.e("xxx", "connect");
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
      Log.e("xxx", "disconnect");
    }
  };

  @Override
  public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
    pluginBinding = binding;
  }

  @Override
  public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
    pluginBinding = null;
  }

  @Override
  public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
    activityBinding = binding;
    setup(
            pluginBinding.getBinaryMessenger(),
            (Application) pluginBinding.getApplicationContext(),
            activityBinding.getActivity(),
            activityBinding);
  }

  @Override
  public void onDetachedFromActivityForConfigChanges() {
    onDetachedFromActivity();
  }

  @Override
  public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
    onAttachedToActivity(binding);
  }

  @Override
  public void onDetachedFromActivity() {
    detach();
  }

  private void setup(
          final BinaryMessenger messenger,
          final Application application,
          final Activity activity,
          final ActivityPluginBinding activityBinding) {
    synchronized (initializationLock) {
      Log.i(TAG, "setup");
      this.activity = activity;
      this.context = application;

      Intent intent = new Intent(activity, PosprinterService.class);
      activity.bindService(intent, mSerconnection, activity.BIND_AUTO_CREATE);

      channel = new MethodChannel(messenger, NAMESPACE + "/methods");
      channel.setMethodCallHandler(this);
      stateChannel = new EventChannel(messenger, NAMESPACE + "/state");
      stateChannel.setStreamHandler(stateStreamHandler);
      EventChannel readChannel = new EventChannel(messenger, NAMESPACE + "/read");
      readChannel.setStreamHandler(readResultsHandler);
      mBluetoothManager = (BluetoothManager) application.getSystemService(Context.BLUETOOTH_SERVICE);
      mBluetoothAdapter = mBluetoothManager.getAdapter();
      activityBinding.addRequestPermissionsResultListener(this);
    }
  }


  private void detach() {
    Log.i(TAG, "detach");
    context = null;
    activityBinding.removeRequestPermissionsResultListener(this);
    activityBinding = null;
    channel.setMethodCallHandler(null);
    channel = null;
    stateChannel.setStreamHandler(null);
    stateChannel = null;
    mBluetoothAdapter = null;
    mBluetoothManager = null;
  }

  // MethodChannel.Result wrapper that responds on the platform thread.
  private static class MethodResultWrapper implements Result {
    private final Result methodResult;
    private final Handler handler;

    MethodResultWrapper(Result result) {
      methodResult = result;
      handler = new Handler(Looper.getMainLooper());
    }

    @Override
    public void success(final Object result) {
      handler.post(() -> methodResult.success(result));
    }

    @Override
    public void error(@NonNull final String errorCode, final String errorMessage, final Object errorDetails) {
      handler.post(() -> methodResult.error(errorCode, errorMessage, errorDetails));
    }

    @Override
    public void notImplemented() {
      handler.post(methodResult::notImplemented);
    }
  }

  @Override
  public void onMethodCall(@NonNull MethodCall call, @NonNull Result rawResult) {
    Result result = new MethodResultWrapper(rawResult);

    if (mBluetoothAdapter == null && !"isAvailable".equals(call.method)) {
      result.error("bluetooth_unavailable", "the device does not have bluetooth", null);
      return;
    }

    final Map<String, Object> arguments = call.arguments();
    switch (call.method) {

      case "state":
        state(result);
        break;

      case "isAvailable":
        result.success(mBluetoothAdapter != null);
        break;

      case "isOn":
        try {
          result.success(mBluetoothAdapter.isEnabled());
        } catch (Exception ex) {
          result.error("Error", ex.getMessage(), exceptionToString(ex));
        }
        break;

      case "isConnected":
        result.success(IS_CONNECT);
        break;

      case "isDeviceConnected":
        if (arguments.containsKey("address")) {
          String address = (String) arguments.get("address");
          isDeviceConnected(result, address);
        } else {
          result.error("invalid_argument", "argument 'address' not found", null);
        }
        break;

      case "openSettings":
        ContextCompat.startActivity(context, new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS),
                null);
        result.success(true);
        break;

      case "getBondedDevices":
        try {

          if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

            if (ContextCompat.checkSelfPermission(activity,
                    Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(activity,
                            Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(activity,
                            Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

              ActivityCompat.requestPermissions(activity,new String[]{
                      Manifest.permission.BLUETOOTH_SCAN,
                      Manifest.permission.BLUETOOTH_CONNECT,
                      Manifest.permission.ACCESS_FINE_LOCATION,
              }, 1);

              pendingResult = result;
              break;
            }
          } else {
            if (ContextCompat.checkSelfPermission(activity,
                    Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED||ContextCompat.checkSelfPermission(activity,
                    Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

              ActivityCompat.requestPermissions(activity,
                      new String[] { Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION }, REQUEST_COARSE_LOCATION_PERMISSIONS);

              pendingResult = result;
              break;
            }
          }
          getBondedDevices(result);

        } catch (Exception ex) {
          result.error("Error", ex.getMessage(), exceptionToString(ex));
        }

        break;

      case "connect":
        if (arguments.containsKey("address")) {
          String address = (String) arguments.get("address");
          connect(result, address);
        } else {
          result.error("invalid_argument", "argument 'address' not found", null);
        }
        break;

      case "disconnect":
        disconnect(result);
        break;

      case "write":
        if (arguments.containsKey("message")) {
          String message = (String) arguments.get("message");
          write(result, message);
        } else {
          result.error("invalid_argument", "argument 'message' not found", null);
        }
        break;

      case "writeBytes":
        if (arguments.containsKey("message")) {
          byte[] message = (byte[]) arguments.get("message");
          writeBytes(result, message);
        } else {
          result.error("invalid_argument", "argument 'message' not found", null);
        }
        break;

      case "printNewLine":
        printNewLine(result);
        break;

      default:
        result.notImplemented();
        break;
    }
  }

  /**
   * @param requestCode  requestCode
   * @param permissions  permissions
   * @param grantResults grantResults
   * @return boolean
   */
  @Override
  public boolean onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

    if (requestCode == REQUEST_COARSE_LOCATION_PERMISSIONS) {
      if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        getBondedDevices(pendingResult);
      } else {
        pendingResult.error("no_permissions", "this plugin requires location permissions for scanning", null);
        pendingResult = null;
      }
      return true;
    }
    return false;
  }

  private void state(Result result) {
    try {
      switch (mBluetoothAdapter.getState()) {
        case BluetoothAdapter.STATE_OFF:
          result.success(BluetoothAdapter.STATE_OFF);
          break;
        case BluetoothAdapter.STATE_ON:
          result.success(BluetoothAdapter.STATE_ON);
          break;
        case BluetoothAdapter.STATE_TURNING_OFF:
          result.success(BluetoothAdapter.STATE_TURNING_OFF);
          break;
        case BluetoothAdapter.STATE_TURNING_ON:
          result.success(BluetoothAdapter.STATE_TURNING_ON);
          break;
        default:
          result.success(0);
          break;
      }
    } catch (SecurityException e) {
      result.error("invalid_argument", "Argument 'address' not found", null);
    }
  }

  /**
   * @param result result
   */
  private void getBondedDevices(Result result) {

    List<Map<String, Object>> list = new ArrayList<>();

    for (BluetoothDevice device : mBluetoothAdapter.getBondedDevices()) {
      Map<String, Object> ret = new HashMap<>();
      ret.put("address", device.getAddress());
      ret.put("name", device.getName());
      ret.put("type", device.getType());
      list.add(ret);
    }

    result.success(list);
  }


  /**
   * @param result  result
   * @param address address
   */
  private void isDeviceConnected(Result result, String address) {

    AsyncTask.execute(() -> {
      try {
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);

        if (device == null) {
          result.error("connect_error", "device not found", null);
          return;
        }

        if (THREAD != null && device.ACTION_ACL_CONNECTED.equals(new Intent(BluetoothDevice.ACTION_ACL_CONNECTED).getAction())) {
          result.success(true);
        }else{
          result.success(false);
        }

      } catch (Exception ex) {
        Log.e(TAG, ex.getMessage(), ex);
        result.error("connect_error", ex.getMessage(), exceptionToString(ex));
      }
    });
  }

  private String exceptionToString(Exception ex) {
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    ex.printStackTrace(pw);
    return sw.toString();
  }

  /**
   * @param result  result
   * @param address address
   */
  private void connect(Result result, String address) {


    AsyncTask.execute(() -> {
      try {
        myBinder.ConnectBtPort(address, new TaskCallback() {
          @Override
          public void OnSucceed() {
            IS_CONNECT = true;
            result.success(true);
          }

          @Override
          public void OnFailed() {
            IS_CONNECT = false;
            result.success(false);
          }
        } );
//        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
//
//        if (device == null) {
//          result.error("connect_error", "device not found", null);
//          return;
//        }
//
//        BluetoothSocket socket = device.createRfcommSocketToServiceRecord(MY_UUID);
//
//        if (socket == null) {
//          result.error("connect_error", "socket connection not established", null);
//          return;
//        }
//
//        // Cancel bt discovery, even though we didn't start it
//        mBluetoothAdapter.cancelDiscovery();
//
//        try {
//          socket.connect();
//          THREAD = new ConnectedThread(socket);
//          THREAD.start();
//          result.success(true);
//        } catch (Exception ex) {
//          Log.e(TAG, ex.getMessage(), ex);
//          result.error("connect_error", ex.getMessage(), exceptionToString(ex));
//        }
      } catch (Exception ex) {
        Log.e(TAG, ex.getMessage(), ex);
        result.error("connect_error", ex.getMessage(), exceptionToString(ex));
      }
    });
  }

  /**
   * @param result result
   */
  private void disconnect(Result result) {
    if (IS_CONNECT) {
      myBinder.DisconnectCurrentPort(new TaskCallback() {
        @Override
        public void OnSucceed() {
          result.success(true);
        }

        @Override
        public void OnFailed() {
          result.success(false);
        }
      });
    }
    return true;

//    if (THREAD == null) {
//      result.error("disconnection_error", "not connected", null);
//      return;
//    }
//    AsyncTask.execute(() -> {
//      try {
//        THREAD.cancel();
//        THREAD = null;
//        result.success(true);
//      } catch (Exception ex) {
//        Log.e(TAG, ex.getMessage(), ex);
//        result.error("disconnection_error", ex.getMessage(), exceptionToString(ex));
//      }
//    });
  }

  /**
   * @param result  result
   * @param message message
   */
  private void write(Result result, String message) {
    if (!IS_CONNECT) {
      result.error("write_error", "not connected", null);
      return;
    }
    if (args.containsKey("config") && args.containsKey("data")) {
      final Map<String,Object> config = (Map<String,Object>) args.get("config");
      final List<Map<String,Objec>> litst = (List<Map<String,Object>>) args.get("data");
      if(list == null) return;

      myBinder.WriteSendData(new TaskCallback() {
        @Override
        public void OnSucceed() {
        }

        @Override
        public void OnFailed() {
          result.error("OnFailed", "Failed sending data to printer", null);
        }
      }, new ProcessData() {
        @Override
        public List<byte[]> processDataBeforeSend() {
          return PrintQRCode.mapToLabel(config, list);
        }
      });
    } else {
      result.error("please add config or data", "", null);
    }

//    try {
//      THREAD.write(message.getBytes());
//      result.success(true);
//    } catch (Exception ex) {
//      Log.e(TAG, ex.getMessage(), ex);
//      result.error("write_error", ex.getMessage(), exceptionToString(ex));
//    }
  }

  private void writeBytes(Result result, byte[] message) {
    if (THREAD == null) {
      result.error("write_error", "not connected", null);
      return;
    }

    try {
      THREAD.write(message);
      result.success(true);
    } catch (Exception ex) {
      Log.e(TAG, ex.getMessage(), ex);
      result.error("write_error", ex.getMessage(), exceptionToString(ex));
    }
  }

  private void printNewLine(Result result) {
    if (THREAD == null) {
      result.error("write_error", "not connected", null);
      return;
    }
//    try {
//      THREAD.write(PrinterCommands.FEED_LINE);
      printTest(result);
      result.success(true);
//    } catch (Exception ex) {
//      Log.e(TAG, ex.getMessage(), ex);
//      result.error("write_error", ex.getMessage(), exceptionToString(ex));
//    }
  }

  private void printTest(final Result result) {
    myBinder.WriteSendData(new TaskCallback() {
      @Override
      public void OnSucceed() {
//        result.success(true);
      }

      @Override
      public void OnFailed() {
//        result.success(false);
      }
    }, new ProcessData() {
      @Override
      public List<byte[]> processDataBeforeSend() {
        List<byte[]> list = new ArrayList<>();
        //设置标签纸大小
        list.add(DataForSendToPrinterTSC.sizeBymm(50,30));
        //设置间隙
        list.add(DataForSendToPrinterTSC.gapBymm(2,0));
        //清除缓存
        list.add(DataForSendToPrinterTSC.cls());
        //设置方向
        list.add(DataForSendToPrinterTSC.direction(0));
        //线条
        list.add(DataForSendToPrinterTSC.bar(10,10,200,3));
        //条码
        list.add(DataForSendToPrinterTSC.barCode(10,45,"128",100,1,0,2,2,"abcdef12345"));
        //文本,简体中文是TSS24.BF2,可参考编程手册中字体的代号
        list.add(DataForSendToPrinterTSC.text(220,10,"TSS24.BF2",0,1,1,"这是测试文本"));
        //打印
        list.add(DataForSendToPrinterTSC.print(1));

        return list;
      }
    });
  }

  private class ConnectedThread extends Thread {
    private final BluetoothSocket mmSocket;
    private final InputStream inputStream;
    private final OutputStream outputStream;

    ConnectedThread(BluetoothSocket socket) {
      mmSocket = socket;
      InputStream tmpIn = null;
      OutputStream tmpOut = null;

      try {
        tmpIn = socket.getInputStream();
        tmpOut = socket.getOutputStream();
      } catch (IOException e) {
        e.printStackTrace();
      }
      inputStream = tmpIn;
      outputStream = tmpOut;
    }

    public void run() {
      byte[] buffer = new byte[1024];
      int bytes;
      while (true) {
        try {
          bytes = inputStream.read(buffer);
          readSink.success(new String(buffer, 0, bytes));
        } catch (NullPointerException e) {
          break;
        } catch (IOException e) {
          break;
        }
      }
    }

    public void write(byte[] bytes) {
      try {
        outputStream.write(bytes);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    public void cancel() {
      try {
        outputStream.flush();
        outputStream.close();

        inputStream.close();

        mmSocket.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  private final StreamHandler stateStreamHandler = new StreamHandler() {

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();

        Log.d(TAG, action);

        if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
          THREAD = null;
          statusSink.success(intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1));
        } else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
          statusSink.success(1);
        } else if (BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED.equals(action)) {
          THREAD = null;
          statusSink.success(2);
        } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
          THREAD = null;
          statusSink.success(0);
        }
      }
    };

    @Override
    public void onListen(Object o, EventSink eventSink) {
      statusSink = eventSink;
      context.registerReceiver(mReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));

      context.registerReceiver(mReceiver, new IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED));

      context.registerReceiver(mReceiver, new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED));

      context.registerReceiver(mReceiver, new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED));

    }

    @Override
    public void onCancel(Object o) {
      statusSink = null;
      context.unregisterReceiver(mReceiver);
    }
  };

  private final StreamHandler readResultsHandler = new StreamHandler() {
    @Override
    public void onListen(Object o, EventSink eventSink) {
      readSink = eventSink;
    }

    @Override
    public void onCancel(Object o) {
      readSink = null;
    }
  };
}