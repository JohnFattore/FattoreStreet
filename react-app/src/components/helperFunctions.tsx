import { IMessage, IOption, ISelection } from "../interfaces"

export function setAlertVarient(message: IMessage) {
    if (message.type == "error")
        return "danger"
    else if (message.type == "success")
        return "success"
    else
        return ""
}

export function selectedOption(option: IOption, selections: ISelection[]) {
    const selectionsFiltered: ISelection[] = selections.filter((selection: ISelection) => selection.option == option.id)
    if (selectionsFiltered.length != 0)
        return ""//"green"
}

// this weeks sunday, sunday last past, is 0
export function getSunday(week: number) {
    function addDays(date, days) {
        var result = new Date(date);
        result.setDate(result.getDate() + days);
        return result;
    }

    function formatDate(date) {
        var dd = String(date.getDate()).padStart(2, '0');
        var mm = String(date.getMonth() + 1).padStart(2, '0'); //January is 0!
        var yyyy = date.getFullYear();
        return yyyy + "-" + mm + "-" + dd;
    }

    var today = new Date();
    var thisSunday = addDays(today, -today.getDay())
    var sunday = addDays(thisSunday, (7 * week))
    return formatDate(sunday)
}