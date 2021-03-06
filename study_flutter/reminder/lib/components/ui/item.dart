import 'package:flutter/material.dart';

class Item extends StatefulWidget {
  late String content;

  Item(String content) {
    this.content = content;
  }

  @override
  State createState() {
    return ItemState();
  }
}

class ItemState extends State<Item> {
  bool isChecked = false;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Checkbox(
            value: isChecked,
            onChanged: (checked) {
              setState(() {
                this.isChecked = checked!;
              });
            }),
        Text(
          widget.content,
          style: TextStyle(
            color: this.isChecked ? Color(0xffcccccc) : Color(0xff000000),
          ),
        )
      ],
    );
  }
}
