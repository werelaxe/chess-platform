let boardWidth = 100;
let boardHeight = 100;
let cellSize = 10;
let width = 10;
let height = 10;
let chosenCoords = null
let boardCanvas = null;
let figures = {};
let board = [];
let ws = null;

let drawers = {
    "CHECKERS": drawCheckersFigure,
    "CLASSIC_CHESS": drawChessFigure,
};


function waitSocket(socket, callback) {
    setTimeout(
        function () {
            let done = false;
            if (socket) {
                if (socket.readyState === 1) {
                    callback();
                    done = true;
                }
            }
            if (!done) {
                waitSocket(socket, callback);
            }
        },
        5);
}



function updateBoard() {
    $.ajax({
        type: 'GET',
        url: "/game/state?id=" + gameId.toString(),
        success: function (data) {
            board = data["board"];
            ensureFigures(board);
            height = board.length;
            width = board[0].length;
            cellSize = Math.min(boardWidth / width, boardHeight / height);
        },
        async: false
    });
}


function highlightCells(suggestions) {
    for (let i = 0; i < suggestions.length; i++) {
        let x = suggestions[i].nums[0];
        let y = suggestions[i].nums[1];
        highlightCell(x, y);
    }
}

function highlightCell(x, y) {
    let ctx = boardCanvas[0].getContext("2d");
    ctx.lineWidth = 5;
    ctx.strokeStyle = "#0f0";
    ctx.strokeRect(x * cellSize, y * cellSize, cellSize, cellSize);
}


function drawCheckersFigure(ctx, fig, i, j) {
    ctx.beginPath();
    ctx.arc(
        i * cellSize + cellSize / 2,
        j * cellSize + cellSize / 2,
        cellSize / 2.5,
        0,
        Math.PI * 2
    );


    if (fig.type === 0) {
        if (fig.owner === 1) {
            ctx.fillStyle = "#000";
            ctx.strokeStyle = "#fff";
        } else {
            ctx.fillStyle = "#fff";
            ctx.strokeStyle = "#000";
        }
    } else {
        if (fig.owner === 1) {
            ctx.fillStyle = "#222";
            ctx.strokeStyle = "#aaa";
        } else {
            ctx.fillStyle = "#ddd";
            ctx.strokeStyle = "#aaa";
        }
    }

    ctx.fill();
    ctx.stroke();

}


let type2imageSelector = {
    "0,1": "#w-pawn",
    "1,1": "#w-bishop",
    "2,1": "#w-knight",
    "3,1": "#w-rook",
    "4,1": "#w-queen",
    "5,1": "#w-king",

    "0,2": "#b-pawn",
    "1,2": "#b-bishop",
    "2,2": "#b-knight",
    "3,2": "#b-rook",
    "4,2": "#b-queen",
    "5,2": "#b-king",
};


function drawChessFigure(ctx, fig, i, j) {
    let type = `${fig.type},${fig.owner}`;
    let image = $(type2imageSelector[type])[0];
    if (image === undefined) {
        console.log(`Can not find image by type ${type}`)
        return;
    }
    ctx.drawImage(image, 10 + i * cellSize, 10 + j * cellSize);
}


function updateBoardCanvas() {
    boardWidth = boardCanvas.width();
    boardHeight = boardCanvas.height();

    updateBoard();

    let ctx = boardCanvas[0].getContext("2d");

    ctx.lineWidth = 1;
    ctx.fillStyle = "#c80";
    ctx.fillRect(0, 0, boardWidth, boardHeight);

    for (let j = 0; j < board.length; j++) {
        for (let i = 0; i < board[0].length; i++) {
            if ((j + i) % 2 === 1) {
                ctx.fillStyle = "#730";
                ctx.fillRect(i * cellSize, j * cellSize, cellSize, cellSize);
            }
            if (board[j][i] !== null) {
                drawers[gameKind](ctx, figures[board[j][i]], i, j);
            }
        }
    }
}


function ensureFigures(board) {
    let ids = [];
    for (let i = 0; i < board.length; i++) {
        for (let j = 0; j < board[0].length; j++) {
            let id = board[i][j];
            if (id !== null && figures[id] === undefined) {
                ids.push(id);
            }
        }
    }
    loadFigures(ids);
}


function loadFigures(ids) {
    let idsArg = ids.map(function(x) { return `ids=${x}` }).join("&");
    let url = `/game/figures?id=${gameId}&${idsArg}`;
    $.ajax({
        type: 'GET',
        url: url,
        success: function (data) {
            for (let i = 0; i < ids.length; i++) {
                figures[ids[i]] = data[i];
            }
        },
        async:false
    });
}


function suggest(x, y) {
    let res = null;
    $.ajax({
        type: 'GET',
        url: `/game/suggest?id=${gameId}&from=${x}&from=${y}`,
        success: function (data) {
            res = data;
        },
        async:false
    });
    return res;
}


function setCanvasClickHandler() {
    let canvasLeft = boardCanvas[0].offsetLeft + boardCanvas[0].clientLeft;
    let canvasTop = boardCanvas[0].offsetTop + boardCanvas[0].clientTop;
    boardCanvas.on("click", function (e) {
        let x = (e.pageX - canvasLeft) / cellSize | 0;
        let y = (e.pageY - canvasTop) / cellSize | 0;
        if (chosenCoords === null) {
            highlightCells(suggest(x, y));
            chosenCoords = [x, y];
        } else {
            let data = JSON.stringify({
                "gameId": gameId,
                "from": {
                    "nums": chosenCoords
                },
                "to": {
                    "nums": [x, y]
                }
            });
            $.ajax({
                type: 'POST',
                url: "/game/step",
                data: data,
                contentType:"application/json; charset=utf-8",
                async:false
            });
            chosenCoords = null;
            sendStepToWs();
        }
    });
}


function sendStepToWs() {
    ws.send(`step:${gameId}`);
}


function sendHelloToWs() {
    ws.send(`new:${gameId}`);
}


function setWsReceivingHandler() {
    ws.onmessage = function (data) {
        updateBoardCanvas();
    }
}


function waitChatSocket() {
    waitSocket(ws, main);
}


function main() {
    boardCanvas = $("#board");
    setCanvasClickHandler();
    updateBoardCanvas();
    setWsReceivingHandler();
    sendHelloToWs();
}


$(document).ready(function() {
    ws = new WebSocket("ws://" + location.host + "/game/notify");
    waitSocket(ws, waitChatSocket);
});
