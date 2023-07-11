const RT_MESSAGE = 'rt_message';
const RT_DISCONNECT = 'rt_disconnect';
const RTPI_MESSAGE = 'rtpi_message';
const RTPI_DISCONNECT = 'rtpi_disconnect';
const PORT = 6181;

var hostname = location.hostname;
var socket = io("http://" + hostname + ":" + PORT);

let bBtn1Pressed = Boolean(false);
//TODO: update utctime field
let pirequestobj = {
      "pirequest": {
        "version": "1.0",
        "utctime": "05/17/2023 3:00:00 PM",
        "locations": [
          {
            "locationid": "1",
            "locationdesc": "Visalia",
            "sortorder": 1,
            "locationtype": "User"
          }
        ],
        "buttons": [
          {
            "number": "1",
            "request": "PRESS"
          }
        ]
      }
    }


function sendMessage() {
  const message = document.getElementById('message').value;
  console.log('Send RT Message:', message);
  socket.emit(RT_MESSAGE, message);
}

function SetupSocketEvents() {
  // #region Socket.io Messages

  socket.on('end', (msg) => {
    alert('Unexpected termination of connection. Trying to establish new connection.');
    console.error('End message received:', msg);
  });

  // #endregion

  // #region Application Messages
    // Main message
    socket.on(RT_MESSAGE, (msg) => {
    console.log('RT Message:', msg);

	var output = document.getElementById("outscroll");

	output.value = msg + "\n" + output.value;
	output.scrollTo(0,0);
 });

    socket.on(RTPI_MESSAGE, (msg) => {
    console.log('RTPI Message:', msg);

	var output = document.getElementById("outscroll");

	output.value = msg + "\n" + output.value;
	output.scrollTo(0,0);
 });


	var btn1 = document.getElementById("pibutton1");
	if (btn1)
	{
        let time;


        // Attach the "pointerdown" event to your button
        btn1.addEventListener('pointerdown', () => {
            if (!bBtn1Pressed)
            {
                time = Date.now();
                bBtn1Pressed = Boolean(true);
                let message = "Button Pressed";
                console.log(message);
                socket.emit(RT_MESSAGE, message);
                pirequestobj.pirequest.buttons[0].request = "PRESS";
                socket.emit(RTPI_MESSAGE,JSON.stringify(pirequestobj));
            }
        });

        // Attach the "pointerup" event to your button
        btn1.addEventListener('pointerup', () => {
            if (bBtn1Pressed)
            {
                bBtn1Pressed = Boolean(false);
                let message = "Button Released\n" + `Pressed Down for ${Date.now() - time} milliseconds`;
                console.log(message);
                socket.emit(RT_MESSAGE, message);
                pirequestobj.pirequest.buttons[0].request = "RELEASE";
                socket.emit(RTPI_MESSAGE,JSON.stringify(pirequestobj));
            }
        });

        // Attach the "pointerout" event to your button
        // Use this event to fix touch screen swipe while pressed
        btn1.addEventListener('pointerout', () => {
            if (bBtn1Pressed)
            {
                bBtn1Pressed = Boolean(false);
                let message = "Button Released-Out\n" + `Pressed Down for ${Date.now() - time} milliseconds`;
                console.log(message);
                socket.emit(RT_MESSAGE, message);
                pirequestobj.pirequest.buttons[0].request = "RELEASE";
                socket.emit(RTPI_MESSAGE,JSON.stringify(pirequestobj));
            }
        });
	}


  // #endregion
}

function Init() {
  SetupSocketEvents();
}
