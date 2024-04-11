import { useEffect, useState } from "react";
import { getOptions } from "./axiosFunctions";
import { IOption } from "../interfaces";

// some things should move up to the table level and be passed down
export default function DjangoRow({ model, setMessage, dispatch, fields }) {

    // handle fields
    let properties = {}
    for (const item in fields) {
        const field = fields[item]
        if (field.function != null) {
            // create function parameter array
            const functionInput: any[] = []
            for (const j in field.parameters) {
                var parameter = field.parameters[j]
                if (parameter in model)
                    functionInput.push(model[parameter])
                else if (parameter in fields)
                    functionInput.push(properties[parameter])
                else
                    functionInput.push(parameter)
            }

            // call function, get field value
            if (field.item != null)
                properties[item] = (field.function(...functionInput)[field.item])
            else
                properties[item] = (field.function(...functionInput))
        }

        // if no function, field is a django model field
        else {
            properties[item] = model[item]
        }
    }

    // turn properties into list, formatted needs to happen down here
    let result: any[] = []
    for (const item in properties) {
        if (fields[item].type == "money") {
            result.push("$".concat(properties[item].toLocaleString(undefined, {
                minimumFractionDigits: 2,
                maximumFractionDigits: 2
            })))
        }
        else if (fields[item].type == "amount") {
            result.push(Number(properties[item]).toFixed(2))
        }
        else
            result.push(properties[item])
    }

    return (
        <tr key={model.id}>
            {result.map((property) => (
                <td>{property}</td>
            ))}
        </tr>)
}