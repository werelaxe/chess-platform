$(document).ready(function() {
    $("#create-btn").on("click", function(e) {
        let data = JSON.stringify({
            "kind": $("#gk").val()
        });
        $.ajax({
            type: 'POST',
            url: "/game/create",
            data: data,
            contentType:"application/json; charset=utf-8",
            success: function (data) {
                let id = data["id"]
                location.href = "/game/play?id=" + id.toString();
            },
            async:false
        });
    });
});
