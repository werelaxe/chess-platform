let boardWidth = 100;
let boardHeight = 100;
let cellSize = 10;
let width = 10;
let height = 10;
let chosenCoords = null
let suggestedCoords = null;
let boardCanvas = null;
let figures = {};
let board = [];
let currentPlayer = null;
let ws = null;
let isQuantum = null;
let isPostQuantum = null;
let timer = null;
let tooltip = null;


let drawers = {
    "CHECKERS": drawCheckersFigure,
    "CLASSIC_CHESS": drawChessFigure,
    "QUANTUM_CHESS": drawQuantumChessFigure,
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


function getPlayerText(player) {
    return player === 1 ? "White" : "Black";
}


function setCurrentPlayer() {
    $("#current-player").text(getPlayerText(currentPlayer));
}


function updateBoard() {
    $.ajax({
        type: 'GET',
        url: "/game/state?id=" + gameId.toString(),
        success: function (data) {
            board = data["board"];
            if (data["postQuantum"] != null) {
                isPostQuantum = data["postQuantum"].nums;
            }
            currentPlayer = data["currentPlayer"];
            setCurrentPlayer(currentPlayer);
            ensureFigures(board);
            height = board.length;
            width = board[0].length;
            cellSize = Math.min(boardWidth / width, boardHeight / height);
        },
        async: false
    });
}


function highlightCells(suggestions, color) {
    for (let i = 0; i < suggestions.length; i++) {
        let x = suggestions[i].nums[0];
        let y = suggestions[i].nums[1];
        highlightCell(x, y, color);
    }
}

function highlightCell(x, y, color) {
    let ctx = boardCanvas[0].getContext("2d");
    ctx.lineWidth = 5;
    ctx.strokeStyle = color;
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


let type2Label = {
    "0,1": "White pawn",
    "1,1": "White bishop",
    "2,1": "White knight",
    "3,1": "White rook",
    "4,1": "White queen",
    "5,1": "White king",

    "0,2": "Black pawn",
    "1,2": "Black bishop",
    "2,2": "Black knight",
    "3,2": "Black rook",
    "4,2": "Black queen",
    "5,2": "Black king",
}


function drawChessFigure(ctx, fig, i, j) {
    let image = getImage(fig);
    ctx.drawImage(image, 10 + i * cellSize, 10 + j * cellSize);
}


function drawFigPiece(ctx, img, x, y, size, offset) {
    ctx.drawImage(img, offset, 0, size, 80, x + offset, y, size, 80);
}


function getImage(pair) {
    let type = `${pair.type},${pair.owner}`;
    let image = $(type2imageSelector[type])[0];
    if (image === undefined) {
        console.log(`Can not find image by type ${type}`)
        return;
    }
    return image;
}


function drawQuantumChessFigure(ctx, fig, i, j) {
    if (fig.figures.length === 1 && fig.figures[0].first === 1) {
        drawChessFigure(ctx, fig.figures[0].second, i, j);
        return;
    }
    let pawnSize = 60;
    let offset = 10;

    for (let k = 0; k < fig.figures.length; k++) {
        let f = fig.figures[k]
        let img = getImage(f.second);
        drawFigPiece(ctx, img, 10 + i * cellSize, 10 + j * cellSize, f.first * pawnSize, offset);
        offset += f.first * pawnSize;
    }
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
    suggestedCoords = res;
    return res;
}


function isSuggestedContains(x, y) {
    for (let i = 0; i < suggestedCoords.length; i++) {
        let coords = suggestedCoords[i].nums;
        if (suggestedCoords[i].nums[0] === x && suggestedCoords[i].nums[1] === y) {
            return true;
        }
    }
    return false;
}


function doStep(x, y) {
    let data = JSON.stringify({
        "gameId": gameId,
        "from": {
            "nums": chosenCoords
        },
        "to": {
            "nums": [x, y]
        },
        "additionalStepInfo": {
            "records": {
                "is_quantum": isQuantum === null ? "false" : "true"
            }
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



function canPickFigure(x, y) {
    if (gameKind === "CLASSIC_CHESS") {
        return figures[board[y][x]].owner === currentPlayer;
    } else {
        let figs = figures[board[y][x]].figures;
        for (let i = 0; i < figs.length; i++) {
            if (figs[i].second.owner === currentPlayer) {
                return true
            }
        }
        return false
    }
}


function setCanvasClickHandler() {
    let canvasLeft = boardCanvas[0].offsetLeft + boardCanvas[0].clientLeft;
    let canvasTop = boardCanvas[0].offsetTop + boardCanvas[0].clientTop;
    boardCanvas.on("click", function (e) {
        let x = (e.pageX - canvasLeft) / cellSize | 0;
        let y = (e.pageY - canvasTop) / cellSize | 0;
        if (board[y][x] !== null) {
            if (chosenCoords !== null && chosenCoords[0] === x && chosenCoords[1] === y && !isSuggestedContains(x, y)) {
                isQuantum = chosenCoords;
                updateBoardCanvas();
                highlightCells(suggest(x, y), "#00f");
                return;
            } else if (chosenCoords !== null && isSuggestedContains(x, y)) {
                doStep(x, y);
                if (isQuantum !== null) {
                    isQuantum = null;
                }
                if (isPostQuantum !== null) {
                    isPostQuantum = null;
                }
                return
            }
            if (canPickFigure(x, y)) {
                updateBoardCanvas();
                highlightCells(suggest(x, y), "#0f0");
                chosenCoords = [x, y];
            }
        }
        if (isPostQuantum !== null) {
            chosenCoords = isPostQuantum;
            updateBoardCanvas();
            highlightCells(suggest(isPostQuantum[0], isPostQuantum[1]), "#00f");
        }
    });

    boardCanvas.on("mousemove", function (e) {
        let x = e.pageX;
        let y = e.pageY;
        hideTooltip();
        moveTooltipTo(x, y);
    });

    $(document).on("mousemove", function (e) {
        mouseX = e.pageX;
        mouseY = e.pageY;
    });
}


let mouseX = 0;
let mouseY = 0;


function isInsideCanvas(x, y) {
    let canvasLeft = boardCanvas[0].offsetLeft + boardCanvas[0].clientLeft;
    let canvasTop = boardCanvas[0].offsetTop + boardCanvas[0].clientTop;
    let canvasWidth = boardCanvas[0].width;
    let canvasHeight = boardCanvas[0].height;
    return x >= canvasLeft && x <= canvasLeft + canvasWidth && y >= canvasTop && y <= canvasTop + canvasHeight;
}


function tooltipHandler(e) {
    let x = (e.pageX);
    let y = (e.pageY);
    moveTooltipTo(x, y);
    if (isInsideCanvas(mouseX, mouseY)) {
        let canvasLeft = boardCanvas[0].offsetLeft + boardCanvas[0].clientLeft;
        let canvasTop = boardCanvas[0].offsetTop + boardCanvas[0].clientTop;

        let cellX = (e.pageX - canvasLeft) / cellSize | 0;
        let cellY = (e.pageY - canvasTop) / cellSize | 0;

        if (figures[board[cellY][cellX]].figures.length === 0) {
            return;
        }
        updateTooltip(cellX, cellY);
        moveTooltipTo(x, y, true);
        showTooltip(cellX, cellY);
    }
}


function getProperty(object, prop) {
    let raw = object.css(prop);
    return parseInt(raw.substring(0, raw.length - 2));
}


function moveTooltipTo(x, y, adjust=true) {
    if (adjust) {
        x += 5;
        let count = (tooltip.html().match(/<br>/g) || []).length + 1;
        y -= count * 18 + 50;
    }
    tooltip.css("left", `${x}px`);
    tooltip.css("top", `${y}px`);
}


function updateTooltip(x, y) {
    let figId = board[y][x];
    let figs = figures[figId];
    let label = [];
    for (let i = 0; i < figs.figures.length; i++) {
        let pair = figs.figures[i].second;
        let type = `${pair.type},${pair.owner}`;
        label.push(`${type2Label[type]}: ${figs.figures[i].first}`);
    }
    tooltip.html(label.join("<br>"));
}


function showTooltip(x, y) {
    tooltip.addClass("show");
}


function hideTooltip() {
    tooltip.removeClass("show");
}


function setTooltipHandler() {
    boardCanvas.on("mousemove", function (e) {
        if (timer) {
            clearTimeout(timer);
        }
        timer = setTimeout(function() {
            tooltipHandler(e);
        }, 1000);
    });
}


function sendStepToWs() {
    ws.send(`step:${gameId}`);
}


function sendHelloToWs() {
    ws.send(`new:${gameId}`);
}


function setWsReceivingHandler() {
    ws.onmessage = function (e) {
        if (e.data.startsWith("winner:")) {
            alert(`${getPlayerText(parseInt(e.data.split(":")[1]))} wins!`);
        } else {
            updateBoardCanvas();
        }
    }
}


function waitChatSocket() {
    waitSocket(ws, main);
}


function initTooltip() {
    tooltip = $("#tooltip");
    tooltip.css("left", 100);
    tooltip.css("top", 100);
    tooltip.addClass("bottom");
    tooltip.addClass("left");
    tooltip.html("Tip!");
}


function main() {
    boardCanvas = $("#board");
    setCanvasClickHandler();
    updateBoardCanvas();
    setWsReceivingHandler();
    sendHelloToWs();
    initTooltip();
    setTooltipHandler();
}


$(document).ready(function() {
    ws = new WebSocket("ws://" + location.host + "/game/notify");
    waitSocket(ws, waitChatSocket);
});
