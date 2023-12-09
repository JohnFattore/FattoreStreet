
function AlbumRow({album}) {
    return (
<tr>
    <td>{album.strName}</td>
    <td>{album.strArtist}</td>
    <td>{album.numYear}</td>
</tr>
    );
}

export default AlbumRow;