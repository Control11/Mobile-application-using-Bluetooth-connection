from network import Bluetooth
from machine import Timer
import pycom

battery = 100
update = False  33 #odpowiada za znacznik, jezeli urzadzenie jest podlaczone to rozladowywuj baterie a jezeli nie to nie rozladowywuj
def conn_cb(chr):
    events = chr.events()
    if events & Bluetooth.CLIENT_CONNECTED:
        print('client connected')
        pycom.heartbeat(True)
    elif events & Bluetooth.CLIENT_DISCONNECTED:
        print('client disconnected')
        pycom.heartbeat(False)
        update = False

def chr1_handler(chr, data):  #ta sekcja to udostepnienie odczytu stanu baterii tego urzadzenia
    global battery
    global update
    events = chr.events()
    print("events: ",events)
    if events & (Bluetooth.CHAR_READ_EVENT | Bluetooth.CHAR_SUBSCRIBE_EVENT):
        chr.value(battery) #wpisanie do atrybutu value, wartosci stanu baterii i przekazanie go do charakterystyki
        print("transmitted :", battery)
        if (events & Bluetooth.CHAR_SUBSCRIBE_EVENT):
            update = True






bluetooth = Bluetooth()
bluetooth.set_advertisement(name='WiPy 3.0', manufacturer_data="Pycom", service_uuid=0xec00)

bluetooth.callback(trigger=Bluetooth.CLIENT_CONNECTED | Bluetooth.CLIENT_DISCONNECTED, handler=conn_cb)
bluetooth.advertise(True)

srv1 = bluetooth.service(uuid=0xec00, isprimary=True,nbr_chars=1)

chr1 = srv1.characteristic(uuid=0xec0e, value='read_from_here') #client reads from here

chr1.callback(trigger=(Bluetooth.CHAR_READ_EVENT | Bluetooth.CHAR_SUBSCRIBE_EVENT), handler=chr1_handler)
print('Start BLE service')










def update_handler(update_alarm): #ta sekcja to imitowanie rozladowywania sie baterii, 
    global battery
    global update
    battery-=1
    if battery == 1:
        battery = 100
    if update:
        chr1.value(str(battery))

update_alarm = Timer.Alarm(update_handler, 1, periodic=True)