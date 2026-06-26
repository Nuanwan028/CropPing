# Discord Bot Setup Guide

วิธีติดตั้ง Discord Bot สำหรับ CropPing

## Step 1: สร้าง Discord Bot

1. ไปที่ [Discord Developer Portal](https://discord.com/developers/applications)
2. คลิก **"New Application"** → ตั้งชื่อ (เช่น "CropPing Bot")
3. ไปที่เมนู **"Bot"** → คลิก **"Add Bot"**
4. คลิก **"Reset Token"** → Copy **Token** เก็บไว้ (นี่คือ `DISCORD_BOT_TOKEN`)

## Step 2: เปิด Privileged Intents

ในหน้า Bot:

1. เลื่อนลงไปที่ **"Privileged Gateway Intents"**
2. เปิด:
   - ✅ **Message Content Intent** (สำคัญ - ให้อ่านข้อความ user ได้)
   - ✅ **Server Members Intent** (ถ้าต้องการ)
   - ✅ **Presence Intent** (ถ้าต้องการ)
3. **Save Changes**

## Step 3: ได้ Application ID

1. ไปที่เมนู **"General Information"**
2. Copy **Application ID** (นี่คือ `DISCORD_APPLICATION_ID`)

## Step 4: เชิญ Bot มาใน Server/DM

### สร้าง Invite Link:

1. ไปที่เมนู **"OAuth2"** → **"URL Generator"**
2. เลือก Scopes:
   - ✅ `bot`
   - ✅ `applications.commands`
3. เลือก Bot Permissions:
   - ✅ `Send Messages`
   - ✅ `Use Slash Commands`
   - ✅ `Read Message History`
   - ✅ `Add Reactions`
4. Copy **Generated URL** ด้านล่าง
5. วางใน Browser → เลือก Server → Authorize

### สำหรับ DM:

User ต้อง:
1. กด Invite Link
2. Authorize Bot
3. DM ไปหา Bot เพื่อเริ่มใช้งาน

## Step 5: ตั้งค่า Environment Variables

```bash
DISCORD_BOT_TOKEN=MTIzNDU2Nzg5MDEyMzQ1Njc4OQ.GhIjKl.MnOpQrStUvWxYzAbCdEf
DISCORD_APPLICATION_ID=123456789012345678
```

หรือแก้ไขไฟล์ `application.properties`:

```properties
discord.bot.token=MTIzNDU2Nzg5MDEyMzQ1Njc4OQ.GhIjKl.MnOpQrStUvWxYzAbCdEf
discord.application.id=123456789012345678
```

## Step 6: ลงทะเบียน Slash Commands

เปิด Browser หรือใช้ curl:

```bash
curl -X PUT \
  "https://discord.com/api/v10/applications/YOUR_APPLICATION_ID/commands" \
  -H "Authorization: Bot YOUR_BOT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '[
    {
      "name": "plant",
      "description": "ปลูกพืช",
      "type": 1
    },
    {
      "name": "list",
      "description": "ดูรายการพืชที่ปลูก",
      "type": 1
    },
    {
      "name": "cancel",
      "description": "ยกเลิกการปลูกพืช",
      "type": 1,
      "options": [{
        "name": "id",
        "description": "รหัสพืช",
        "type": 4,
        "required": true
      }]
    },
    {
      "name": "cancel_all",
      "description": "ยกเลิกทั้งหมด",
      "type": 1
    }
  ]'
```

หรือใช้ `DiscordService.registerSlashCommands()` โดยเรียกครั้งเดียวตอนเริ่ม app

## Step 7: ตั้งค่า Webhook URL

ใน Discord Developer Portal:

1. เลือก Application ของคุณ
2. ไปที่เมนู **"Interactions"** → **"Endpoints"**
3. ใส่ URL:
   ```
   https://your-server.com/discord/interactions
   ```
4. **Save Changes**

⚠️ **ต้องใช้ HTTPS** - Discord ไม่รับ HTTP

## Step 8: ทดสอบ

Start Server แล้วลองใน Discord:

```
/plant paddy      - ปลูกข้าว
/list             - ดูรายการ
/cancel 1         - ยกเลิก ID 1
/cancel_all       - ลบทั้งหมด
```

## Commands

| Command | คำอธิบาย |
|---------|----------|
| `/plant` | เปิดเมนูเลือกพืช |
| `/plant <crop>` | ปลูกพืชที่ต้องการ |
| `/list` | ดูรายการพืชที่ปลูกอยู่ |
| `/cancel <id>` | ยกเลิกพืชตาม ID |
| `/cancel_all` | ยกเลิกทั้งหมด |

## Troubleshooting

### Bot ไม่ตอบ
- ตรวจสอบว่าเปิด **Message Content Intent** แล้ว
- ตรวจสอบว่า Webhook URL ถูกต้อง
- ตรวจสอบ logs

### Slash Commands ไม่แสดง
- ตรวจสอบว่าลงทะเบียน commands แล้ว
- รอประมาณ 1 ชั่วโมงให้ Discord sync
- ลอง restart Discord app

### ได้รับ "Invalid Interaction"
- ตรวจสอบว่าใส่ Bot Token ถูกต้อง
- ตรวจสอบว่า Application ID ถูกต้อง

## Resources

- [Discord API Documentation](https://discord.com/developers/docs/intro)
- [Discord Bot Guide](https://discordjs.guide/)
- [Interaction Endpoint](https://discord.com/developers/docs/interactions/receiving-and-responding)
