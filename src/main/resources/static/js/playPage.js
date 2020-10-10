let boardWidth = 100;
let boardHeight = 100;
let cellSize = 10;
let width = 10;
let height = 10;
let chosenCoords = null
let boardCanvas = null;
let figures = {};


$(document).ready(function() {
    boardCanvas = $("#board");
    setCanvasClickHandler();
    updateBoard();
});


function updateBoard() {
    let ctx = boardCanvas[0].getContext("2d");

    boardWidth = boardCanvas.width();
    boardHeight = boardCanvas.height();

    $.ajax({
        type: 'GET',
        url: "/game/state?id=" + gameId.toString(),
        success: function (data) {
            let board = data["board"];
            ensureFigures(board);

            height = board.length;
            width = board[0].length;

            cellSize = Math.min(boardWidth / width, boardHeight / height);

            ctx.fillStyle = "#c80";
            ctx.fillRect(0, 0, boardWidth, boardHeight);

            for (let j = 0; j < board.length; j++) {
                for (let i = 0; i < board[0].length; i++) {
                    if ((j + i) % 2 === 0) {
                        ctx.strokeRect(i * cellSize, j * cellSize, cellSize, cellSize);
                    } else {
                        ctx.fillStyle = "#730";
                        ctx.fillRect(i * cellSize, j * cellSize, cellSize, cellSize);
                    }
                    if (board[j][i] !== null) {
                        ctx.beginPath();
                        ctx.arc(
                            i * cellSize + cellSize / 2,
                            j * cellSize + cellSize / 2,
                            cellSize / 2.5,
                            0,
                            Math.PI * 2
                        );
                        let fig = figures[board[j][i]];
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
                }
            }
        },
        async:false
    });
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


function setCanvasClickHandler() {
    let canvasLeft = boardCanvas[0].offsetLeft + boardCanvas[0].clientLeft;
    let canvasTop = boardCanvas[0].offsetTop + boardCanvas[0].clientTop;
    boardCanvas.on("click", function (e) {
        let x = (e.pageX - canvasLeft) / cellSize | 0;
        let y = (e.pageY - canvasTop) / cellSize | 0;
        if (chosenCoords === null) {
            console.log("Now choose coords: " + [x, y].toString());
            chosenCoords = [x, y];
        } else {
            console.log("Send post");
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
                success: function (data) {
                    console.log(data);
                },
                async:false
            });
            console.log("Step from " + chosenCoords.toString() + " to " + [x, y].toString());
            chosenCoords = null;
            updateBoard();
        }
    });
}
