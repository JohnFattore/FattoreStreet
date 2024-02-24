import { IMessage } from "../interfaces"

export function setAlertVarient (message: IMessage) {
    if (message.type == "error")
        return "danger"
    else if (message.type == "success")    
        return "success"
    else
        return ""
}