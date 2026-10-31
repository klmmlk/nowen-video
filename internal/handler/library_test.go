package handler

import (
	"fmt"
	"io/fs"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/nowen-video/nowen-video/internal/service"
	"go.uber.org/zap"
)

// fakeRemoteVFS 模拟远程存储：dirs 中的路径视为目录，files 中的路径视为文件，其余不存在
type fakeRemoteVFS struct {
	dirs  map[string]bool
	files map[string]bool
}

func (f *fakeRemoteVFS) Type() string { return "alist" }

func (f *fakeRemoteVFS) Stat(p string) (fs.FileInfo, error) {
	if f.dirs[p] {
		return &fakeRemoteFileInfo{name: filepath.Base(p), isDir: true}, nil
	}
	if f.files[p] {
		return &fakeRemoteFileInfo{name: filepath.Base(p), isDir: false}, nil
	}
	return nil, fmt.Errorf("远程对象不存在: %s", p)
}

func (f *fakeRemoteVFS) Open(p string) (service.VFSFile, error) {
	return nil, fmt.Errorf("not implemented")
}

func (f *fakeRemoteVFS) ReadDir(p string) ([]fs.DirEntry, error) {
	return nil, fmt.Errorf("not implemented")
}

func (f *fakeRemoteVFS) Walk(root string, fn filepath.WalkFunc) error {
	return fmt.Errorf("not implemented")
}

// fakeRemoteFileInfo 实现 os.FileInfo
type fakeRemoteFileInfo struct {
	name  string
	isDir bool
}

func (f *fakeRemoteFileInfo) Name() string               { return f.name }
func (f *fakeRemoteFileInfo) Size() int64                { return 0 }
func (f *fakeRemoteFileInfo) Mode() fs.FileMode          { return fs.ModeDir | 0555 }
func (f *fakeRemoteFileInfo) ModTime() time.Time         { return time.Time{} }
func (f *fakeRemoteFileInfo) IsDir() bool                { return f.isDir }
func (f *fakeRemoteFileInfo) Sys() interface{}           { return nil }

func newTestLibraryHandler(mgr *service.VFSManager) *LibraryHandler {
	return &LibraryHandler{vfsMgr: mgr, logger: zap.NewNop().Sugar()}
}

func TestValidateLibraryPath_LocalPath(t *testing.T) {
	h := newTestLibraryHandler(nil)

	dir := t.TempDir()
	if err := h.validateLibraryPath(dir); err != nil {
		t.Fatalf("本地存在的目录应通过校验，得到: %v", err)
	}

	missing := filepath.Join(dir, "not-exist")
	err := h.validateLibraryPath(missing)
	if err == nil || !strings.Contains(err.Error(), "路径不存在") {
		t.Fatalf("本地不存在的路径应报\"路径不存在\"，得到: %v", err)
	}

	// 本地文件（非目录）
	file := filepath.Join(dir, "a.txt")
	if err := os.WriteFile(file, []byte("x"), 0644); err != nil {
		t.Fatal(err)
	}
	err = h.validateLibraryPath(file)
	if err == nil || !strings.Contains(err.Error(), "路径不是目录") {
		t.Fatalf("本地文件路径应报\"路径不是目录\"，得到: %v", err)
	}
}

func TestValidateLibraryPath_RemotePath(t *testing.T) {
	mgr := service.NewVFSManager(zap.NewNop().Sugar())
	mgr.SetGlobalAlistFS(&fakeRemoteVFS{
		dirs:  map[string]bool{"/电影": true},
		files: map[string]bool{"/电影/file.mp4": true},
	})
	h := newTestLibraryHandler(mgr)

	// 远端存在的目录
	if err := h.validateLibraryPath("alist://电影"); err != nil {
		t.Fatalf("alist:// 已存在目录应通过校验，得到: %v", err)
	}

	// 远端不存在的目录
	err := h.validateLibraryPath("alist://不存在")
	if err == nil || !strings.Contains(err.Error(), "远程路径不可访问") {
		t.Fatalf("alist:// 不存在的目录应报\"远程路径不可访问\"，得到: %v", err)
	}

	// 远端文件（非目录）
	err = h.validateLibraryPath("alist://电影/file.mp4")
	if err == nil || !strings.Contains(err.Error(), "远程路径不是目录") {
		t.Fatalf("远程文件路径应报\"远程路径不是目录\"，得到: %v", err)
	}

	// 对应远程存储未启用（S3 未注入，回退 LocalFS）
	err = h.validateLibraryPath("s3://bucket/media")
	if err == nil || !strings.Contains(err.Error(), "无法解析") {
		t.Fatalf("未启用的远程存储应报\"无法解析\"，得到: %v", err)
	}
}

func TestValidateLibraryPath_RemoteWithoutVFSManager(t *testing.T) {
	// vfsMgr 未注入时放行远程路径，交由扫描阶段兜底
	h := newTestLibraryHandler(nil)
	if err := h.validateLibraryPath("alist://电影"); err != nil {
		t.Fatalf("vfsMgr 为 nil 时应放行远程路径，得到: %v", err)
	}
}
