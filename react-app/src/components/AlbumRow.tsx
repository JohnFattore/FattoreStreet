
function AlbumRow({album}) {
    return (
<tr>
    <td>{album.name}</td>
    <td>{album.artist}</td>
    <td>{album.year}</td>
</tr>
    );
}

export default AlbumRow;