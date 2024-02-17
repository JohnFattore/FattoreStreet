import getToy from "./getToy"

export default function Lucy() {
    const toy = getToy()
    return (
        <h3 role='toy'>{toy}</h3>
    );
}