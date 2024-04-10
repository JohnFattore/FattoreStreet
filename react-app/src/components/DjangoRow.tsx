import { useEffect, useState } from "react";
import { getOptions } from "./axiosFunctions";
import { IOption } from "../interfaces";


export default function DjangoRow({ model, setMessage, dispatch, extraFields, excludeFields }) {

    console.log(model)

    let properties: String[] = []
    for (const property in model) {
        if (!(excludeFields.includes(property)))
            properties.push(model[property])
    }

    for (const index in extraFields) {
        properties.push(extraFields[index].function(extraFields[index].field))
    }

    return (
        <tr key={model.id}>
            {properties.map((property) => (
                <td>{property}</td>
            ))}
        </tr>
    )
}