import 'dart:typed_data';
import 'package:blue_thermal_printer_example/printerenum.dart';
import 'package:flutter/services.dart';
import 'package:path_provider/path_provider.dart';
import 'package:blue_thermal_printer/blue_thermal_printer.dart';
import 'dart:io';
import 'package:http/http.dart' as http;

class LineText {
  LineText(
      {this.type, //text,barcode,qrcode,image(base64 string)
      this.content,
      this.size = 0,
      this.align = ALIGN_LEFT,
      this.weight = 0, //0,1
      this.width = 0, //0,1
      this.height = 0, //0,1
      this.underline = 0, //0,1
      this.linefeed = 0, //0,1
      this.x = 0,
      this.y = 0,
      this.font_type = "TSS24.BF2",
      this.rotation = 0,
      this.x_multification = 1,
      this.y_multification = 1});

  static const String TYPE_TEXT = 'text';
  static const String TYPE_BARCODE = 'barcode';
  static const String TYPE_QRCODE = 'qrcode';
  static const String TYPE_IMAGE = 'image';
  static const int ALIGN_LEFT = 0;
  static const int ALIGN_CENTER = 1;
  static const int ALIGN_RIGHT = 2;

  final String? type;
  final String? content;
  final int? size;
  final int? align;
  final int? weight;
  final int? width;
  final int? height;
  final int? underline;
  final int? linefeed;
  final int? x;
  final int? y;
  final String? font_type;
  final int? rotation;
  final int? x_multification;
  final int? y_multification;

  factory LineText.fromJson(Map<String, dynamic> json) {
    return LineText(
      type: json['type'] as String?,
      content: json['content'] as String?,
      size: json['size'] as int?,
      align: json['align'] as int?,
      weight: json['weight'] as int?,
      width: json['width'] as int?,
      height: json['height'] as int?,
      underline: json['underline'] as int?,
      linefeed: json['linefeed'] as int?,
      x: json['x'] as int?,
      y: json['y'] as int?,
      x_multification: json['x_multification'] as int?,
      y_multification: json['y_multification'] as int?,
      font_type: json['font_type'] as String?,
      rotation: json['rotation'] as int?,
    );
  }

  Map<String, dynamic> toJson(LineText data) {
    final val = <String, dynamic>{};

    void writeNotNull(String key, dynamic value) {
      if (value != null) {
        val[key] = value;
      }
    }

    writeNotNull('type', data.type);
    writeNotNull('content', data.content);
    writeNotNull('size', data.size);
    writeNotNull('align', data.align);
    writeNotNull('weight', data.weight);
    writeNotNull('width', data.width);
    writeNotNull('height', data.height);
    writeNotNull('underline', data.underline);
    writeNotNull('linefeed', data.linefeed);
    writeNotNull('x', data.x);
    writeNotNull('y', data.y);
    writeNotNull('x_multification', data.x_multification);
    writeNotNull('y_multification', data.y_multification);
    writeNotNull('font_type', data.font_type);
    writeNotNull('rotation', data.font_type);
    return val;
  }
}

///Test printing
class TestPrint {
  BlueThermalPrinter bluetooth = BlueThermalPrinter.instance;

  sample() async {
    //image max 300px X 300px

    ///image from File path
    // String filename = 'yourlogo.png';
    // ByteData bytesData = await rootBundle.load("assets/images/yourlogo.png");
    // String dir = (await getApplicationDocumentsDirectory()).path;
    // File file = await File('$dir/$filename').writeAsBytes(bytesData.buffer
    //     .asUint8List(bytesData.offsetInBytes, bytesData.lengthInBytes));
    //
    // ///image from Asset
    // ByteData bytesAsset = await rootBundle.load("assets/images/yourlogo.png");
    // Uint8List imageBytesFromAsset = bytesAsset.buffer
    //     .asUint8List(bytesAsset.offsetInBytes, bytesAsset.lengthInBytes);
    //
    // ///image from Network
    // var response = await http.get(Uri.parse(
    //     "https://raw.githubusercontent.com/kakzaki/blue_thermal_printer/master/example/assets/images/yourlogo.png"));
    // Uint8List bytesNetwork = response.bodyBytes;
    // Uint8List imageBytesFromNetwork = bytesNetwork.buffer
    //     .asUint8List(bytesNetwork.offsetInBytes, bytesNetwork.lengthInBytes);

    bluetooth.isConnected.then((isConnected) {
      if (isConnected == true) {
        Map<String, dynamic> config = Map();
        config['width'] = 50; // 标签宽度，单位mm
        config['height'] = 30; // 标签高度，单位mm
        config['gap'] = 2; // 标签间隔，单位mm

        // x、y坐标位置，单位dpi，1mm=8dpi
        List<LineText> list = [];
        list.add(LineText(
            type: LineText.TYPE_TEXT, x: 140, y: 10, content: 'ABCDEF'));
        list.add(LineText(
            type: LineText.TYPE_TEXT, x: 100, y: 40, content: '一二三四五'));
        list.add(LineText(
            type: LineText.TYPE_QRCODE,
            x: 120,
            y: 80,
            size: 5,
            content: 'Content was Here'));
        Map<String, Object> args = Map();
        args['config'] = config;
        args['data'] = list.map((e) => LineText().toJson(e)).toList();
        print('args --- $args');
        bluetooth.write(args);
        // bluetooth.printNewLine();

        // bluetooth.printCustom("HEADER", Size.boldMedium.val, Align.center.val);
        // bluetooth.printNewLine();
        // bluetooth.printImage(file.path); //path of your image/logo
        // bluetooth.printNewLine();
        // bluetooth.printImageBytes(imageBytesFromAsset); //image from Asset
        // bluetooth.printNewLine();
        // bluetooth.printImageBytes(imageBytesFromNetwork); //image from Network
        // bluetooth.printNewLine();
        // bluetooth.printLeftRight("LEFT", "RIGHT", Size.medium.val);
        // bluetooth.printLeftRight("LEFT", "RIGHT", Size.bold.val);
        // bluetooth.printLeftRight("LEFT", "RIGHT", Size.bold.val,
        //     format:
        //         "%-15s %15s %n"); //15 is number off character from left or right
        // bluetooth.printNewLine();
        // bluetooth.printLeftRight("LEFT", "RIGHT", Size.boldMedium.val);
        // bluetooth.printLeftRight("LEFT", "RIGHT", Size.boldLarge.val);
        // bluetooth.printLeftRight("LEFT", "RIGHT", Size.extraLarge.val);
        // bluetooth.printNewLine();
        // bluetooth.print3Column("Col1", "Col2", "Col3", Size.bold.val);
        // bluetooth.print3Column("Col1", "Col2", "Col3", Size.bold.val,
        //     format:
        //         "%-10s %10s %10s %n"); //10 is number off character from left center and right
        // bluetooth.printNewLine();
        // bluetooth.print4Column("Col1", "Col2", "Col3", "Col4", Size.bold.val);
        // bluetooth.print4Column("Col1", "Col2", "Col3", "Col4", Size.bold.val,
        //     format: "%-8s %7s %7s %7s %n");
        // bluetooth.printNewLine();
        // bluetooth.printCustom("čĆžŽšŠ-H-ščđ", Size.bold.val, Align.center.val,
        //     charset: "windows-1250");
        // bluetooth.printLeftRight("Številka:", "18000001", Size.bold.val,
        //     charset: "windows-1250");
        // bluetooth.printCustom("Body left", Size.bold.val, Align.left.val);
        // bluetooth.printCustom("Body right", Size.medium.val, Align.right.val);
        // bluetooth.printNewLine();
        // bluetooth.printCustom("Thank You", Size.bold.val, Align.center.val);
        // bluetooth.printNewLine();
        // bluetooth.printQRcode(
        //     "Insert Your Own Text to Generate", 200, 200, Align.center.val);
        // bluetooth.printNewLine();
        // bluetooth.printNewLine();
        // bluetooth
        //     .paperCut(); //some printer not supported (sometime making image not centered)
        //bluetooth.drawerPin2(); // or you can use bluetooth.drawerPin5();
      }
    });
  }

//   sample(String pathImage) async {
//     //SIZE
//     // 0- normal size text
//     // 1- only bold text
//     // 2- bold with medium text
//     // 3- bold with large text
//     //ALIGN
//     // 0- ESC_ALIGN_LEFT
//     // 1- ESC_ALIGN_CENTER
//     // 2- ESC_ALIGN_RIGHT
//
// //     var response = await http.get("IMAGE_URL");
// //     Uint8List bytes = response.bodyBytes;
//     bluetooth.isConnected.then((isConnected) {
//       if (isConnected == true) {
//         bluetooth.printNewLine();
//         bluetooth.printCustom("HEADER", 3, 1);
//         bluetooth.printNewLine();
//         bluetooth.printImage(pathImage); //path of your image/logo
//         bluetooth.printNewLine();
// //      bluetooth.printImageBytes(bytes.buffer.asUint8List(bytes.offsetInBytes, bytes.lengthInBytes));
//         bluetooth.printLeftRight("LEFT", "RIGHT", 0);
//         bluetooth.printLeftRight("LEFT", "RIGHT", 1);
//         bluetooth.printLeftRight("LEFT", "RIGHT", 1, format: "%-15s %15s %n");
//         bluetooth.printNewLine();
//         bluetooth.printLeftRight("LEFT", "RIGHT", 2);
//         bluetooth.printLeftRight("LEFT", "RIGHT", 3);
//         bluetooth.printLeftRight("LEFT", "RIGHT", 4);
//         bluetooth.printNewLine();
//         bluetooth.print3Column("Col1", "Col2", "Col3", 1);
//         bluetooth.print3Column("Col1", "Col2", "Col3", 1,
//             format: "%-10s %10s %10s %n");
//         bluetooth.printNewLine();
//         bluetooth.print4Column("Col1", "Col2", "Col3", "Col4", 1);
//         bluetooth.print4Column("Col1", "Col2", "Col3", "Col4", 1,
//             format: "%-8s %7s %7s %7s %n");
//         bluetooth.printNewLine();
//         String testString = " čĆžŽšŠ-H-ščđ";
//         bluetooth.printCustom(testString, 1, 1, charset: "windows-1250");
//         bluetooth.printLeftRight("Številka:", "18000001", 1,
//             charset: "windows-1250");
//         bluetooth.printCustom("Body left", 1, 0);
//         bluetooth.printCustom("Body right", 0, 2);
//         bluetooth.printNewLine();
//         bluetooth.printCustom("Thank You", 2, 1);
//         bluetooth.printNewLine();
//         bluetooth.printQRcode("Insert Your Own Text to Generate", 200, 200, 1);
//         bluetooth.printNewLine();
//         bluetooth.printNewLine();
//         bluetooth.paperCut();
//       }
//     });
//   }
}
